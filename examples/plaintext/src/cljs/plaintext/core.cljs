(ns plaintext.core
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET POST]]
            [othello.store :as store]
            [othello.operations :as operations]
            [othello.documents :as document]
            [othello.composers :as composers]
            [cljs.core.async :refer [put! chan <!]]
            [taoensso.sente :as sente :refer (cb-success?)]
            [goog.string.StringBuffer])
  (:require-macros [othello.operations :refer (defops)]
                   [cljs.core.async.macros :as asyncm :refer (go go-loop)]))

;; ========= Set initial state of app =========

(defonce app-state
  (atom {:text "Operational Transform Editor"
         :local-document  {:text nil}
         :sync {:last-seen-id nil
                :buffer []
                :pending-operation []}}))

;; ========= Native JS =========

(defn $editor [] (.getElementById js/document "editor"))

(defn caret-position
  "gets or sets the current cursor position"
  ([]
   (js/parseInt (.-selectionStart ($editor)) 10))
  ([new-pos]
   (.setSelectionRange ($editor) new-pos new-pos)))

;; https://github.com/davesann/cljs-uuid/blob/master/src/cljs_uuid/core.cljs
(defn make-uuid
  []
  (letfn [(f [] (.toString (rand-int 16) 16))
          (g [] (.toString  (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16))]
    (uuid (.toString
            (goog.string.StringBuffer.
             (f) (f) (f) (f) (f) (f) (f) (f) "-" (f) (f) (f) (f)
             "-4" (f) (f) (f) "-" (g) (f) (f) (f) "-"
             (f) (f) (f) (f) (f) (f) (f) (f) (f) (f) (f) (f))))))

;; ======== Set up the socket ========

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :ws})]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )


;; ========= Document processing =========

(def compose-operations (partial reduce composers/compose))

(defn pending? []
  (boolean (seq (get-in @app-state [:sync :pending-operation]))))

(declare sync!)

(defn flush-buffer! []
  (let [buffer (get-in @app-state [:sync :buffer])]
    (swap! app-state assoc-in [:sync :pending-operation] [])
    (swap! app-state assoc-in [:sync :buffer] [])
    (when (seq buffer)
      (sync! (compose-operations buffer)))))

(defn ack! [id]
  (swap! app-state assoc-in [:sync :last-seen-id] id)
  (flush-buffer!))

(defn sync! [opdata]
  (let [client-id (make-uuid)]
    (swap! app-state assoc-in [:sync :pending-operation] {:client-id client-id :operation opdata})
    (let [last-seen-id (get-in @app-state [:sync :last-seen-id])
          package {:operations opdata :parent-id last-seen-id :client-id client-id}]
      (chsk-send! [:document/some-id package] 8000))))

(defn append-to-buf! [operation]
  (swap! app-state update-in [:sync :buffer] conj operation))

(defn apply! [operation]
  (swap! app-state update-in [:local-document :text] document/apply-ops operation))

(defn insert! [operation]
  "Takes a vector of Operations to apply locally"
  (if (pending?)
    (append-to-buf! operation)
    (sync! operation))
  (apply! operation))

(defn make-insert [char position]
  (let [current-text (get-in @app-state [:local-document :text])
        remaining (- (count current-text) position)]
    (cond-> []
      (pos? position)  (into (defops ::operations/ret position))
      true             (into (defops ::operations/ins char))
      (pos? remaining) (into (defops ::operations/ret remaining)))))

(defn make-delete [position]
  (let [current-text (get-in @app-state [:local-document :text])
        remaining (- (count current-text) position)
        ops (defops ::operations/ret (dec position) ::operations/del 1)]
    (if (pos? remaining)
      (into ops (defops ::operations/ret remaining))
      ops)))

(defn receive [{:keys [id client-id operations]}]
  (let [pending-operation (get-in @app-state [:sync :pending-operation :client-id])]
    (if (and (not (nil? pending-operation)) (= client-id pending-operation))
      (ack! id)
      (do (apply! operations)
          (when (not (pending?)) ;; need to fix for local change when buffering
            (swap! app-state assoc-in [:sync :last-seen-id] id))))))

;; ========= Build out UI =========

(defn keypress-handler [event]
  (when-not (some #(= (.-keyCode event) %) [8 37 38 39 40])
    (let [char (.fromCharCode js/String (.-which event))
          cursor (caret-position)]
      (swap! app-state assoc-in [:local-document :cursor] (inc cursor))
      (insert! (make-insert char cursor)))))

(defn keydown-handler [event]
  (when (= (.-which event) 8)
    (let [cursor (caret-position)]
      (swap! app-state assoc-in [:local-document :cursor] (dec cursor))
      (insert! (make-delete cursor)))))

(defn editor-input []
  (reagent/create-class
   {:component-did-update
    (fn [] (let [cursor (get-in @app-state [:local-document :cursor])]
            (caret-position cursor)))
    :reagent-render
    (fn []
      [:textarea {:id "editor"
                  :on-click #(swap! app-state assoc-in [:local-document :cursor] (caret-position))
                  :on-key-down #(keydown-handler %)
                  :on-key-press #(keypress-handler %)
                  :value (get-in @app-state [:local-document :text])}])}))

(defn buffer-log []
  [:div
   [:h2 "Syncing"]
   [:ul [:li (pr-str (get-in @app-state [:sync :pending-operation]))]]
   [:h2 "Buffer"]
   (if (seq (get-in @app-state [:sync :buffer]))
     [:ul (for [operation (get-in @app-state [:sync :buffer])]
            [:li (pr-str operation)])]
     [:ul [:li "Buffer is empty"]])])

(defn greeting []
  [:div
   [:h1 (:text @app-state)]
   [:p (str "Last seen parent: " (get-in @app-state [:sync :last-seen-id]))]
   [editor-input]
   (when (pending?) [buffer-log])])


;; ========= Set up some initialization =========

(defn fetch-remote-document []
  (GET "/document.json"
      :response-format :json
      :keywords? true
      :handler (fn [{:keys [initial-text last-id]}]
                 (swap! app-state assoc-in [:sync :last-seen-id] (uuid (.toString last-id)))
                 (swap! app-state assoc-in [:local-document :text] initial-text))))

(defn recv-queue []
  (go-loop []
    (let [{:as ev-msg [_ event] :event} (<! ch-chsk)
          handle (first event)]
      (case handle
        :editor/operation (receive (second event))
        :browser/refresh  (.reload js/location)
        (println "unhandled event" event))
      (recur))))

(defn run []
  (reagent/render [greeting] (js/document.getElementById "app")))

(defn init []
  (fetch-remote-document)
  (recv-queue)
  (run))

;; ========= Kick things off =========

(init)
