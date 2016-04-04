(ns plaintext.core
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET POST]]
            [othello.store :as store]
            [othello.operations :as operations]
            [othello.documents :as document]
            [othello.composers :as composers]
            [cljs.core.async :refer [put! chan <!]]
            [taoensso.sente :as sente :refer (cb-success?)])
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
   (let [el (aget (.-childNodes ($editor)) 0)
         range (.createRange js/document)
         sel (.getSelection js/document)]
     (when el
       (.setStart range el new-pos)
       (.collapse range true)
       (.removeAllRanges sel)
       (.addRange sel range)))))

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

(declare push!)

(defn flush-buffer! []
  (println "flushing buffer")
  (let [buffer (get-in @app-state [:sync :buffer])]
    (swap! app-state assoc-in [:sync :pending-operation] [])
    (swap! app-state assoc-in [:sync :buffer] [])
    (when (seq buffer)
      (push! (compose-operations buffer)))))

(defn sync-done [reply]
  (when (cb-success? reply)
    (swap! app-state assoc-in [:sync :last-seen-id] (:id reply))
    (flush-buffer!)))

(defn push! [opdata]
  (swap! app-state assoc-in [:sync :pending-operation] opdata)
  (let [last-seen-id (get-in @app-state [:sync :last-seen-id])
        package {:operations opdata :parent-id last-seen-id}]
    (chsk-send! [:document/some-id package] 8000)))

(defn append-to-buf! [operation]
  (swap! app-state update-in [:sync :buffer] conj operation))

(defn apply! [operation]
  (swap! app-state update-in [:local-document :text] document/apply-ops operation))

(defn insert! [operation & {:keys [local?] :or {local? false}}]
  "Takes a vector of Operations to apply locally"
  (if (pending?)
    (append-to-buf! operation)
    (push! operation))
  (apply! operation))

(defn insert-operation [char position]
  (let [current-text (get-in @app-state [:local-document :text])
        remaining (- (count current-text) position)]
    (cond-> []
      (pos? position)  (into (defops ::operations/ret position))
      true             (into (defops ::operations/ins char))
      (pos? remaining) (into (defops ::operations/ret remaining)))))

(defn recv-queue []
  (go-loop []
    (let [{:as ev-msg [_ data] :event} (<! ch-chsk)]
      (println data)
      (recur))))

;; ========= Build out UI =========

(defn key-handler [event]
  (.preventDefault event)
  (when-not (some #(= (.-keyCode event) %) [8 37 38 39 40])
    (let [char (.fromCharCode js/String (.-which event))]
      (insert! (insert-operation char (caret-position))))))

(defn greeting []
  [:div
   [:h1 (:text @app-state)]
   [:p (str "Last seen parent: " (get-in @app-state [:sync :last-seen-id]))]
   [:textarea {:id "editor"
               :on-key-press #(key-handler %)
               :value (get-in @app-state [:local-document :text])}]
   (when (pending?)
     [:div
      [:h2 "Syncing"]
      [:ul [:li (pr-str (get-in @app-state [:sync :pending-operation]))]]
      [:h2 "Buffer"]
      (if (seq (get-in @app-state [:sync :buffer]))
        [:ul (for [operation (get-in @app-state [:sync :buffer])]
               [:li (pr-str operation)])]
        [:ul [:li "Buffer is empty"]])])])


;; ========= Set up some initialization =========

(defn fetch-remote-document []
  (GET "/document.json"
      :response-format :json
      :keywords? true
      :handler (fn [{:keys [initial-text last-id]}]
                 (swap! app-state assoc-in [:sync :last-seen-id] last-id)
                 (swap! app-state assoc-in [:local-document :text] initial-text))))

(defn run []
  (reagent/render [greeting] (js/document.getElementById "app")))

(defn init []
  (fetch-remote-document)
  (recv-queue)
  (run))


;; ========= Kick things off =========

(init)
