(ns plaintext.core
  (:require [reagent.core :as reagent :refer [atom]]
            [ajax.core :refer [GET POST]]
            [othello.store :as store]
            [othello.operations :as operations]
            [othello.documents :as document]
            [othello.composers :as composers])
  (:require-macros [othello.operations :refer (defops)]))

;; Set initial state of app
(defonce app-state (atom {:text "Hello Chestnut!"
                          :local-document  {:buffer [] :text nil :pending? false}
                          :remote-document {:last-id nil}}))

;; Native JS
(defn $editor []
  (.getElementById js/document "editor"))

(defn caret-position
  "gets or sets the current cursor position"
  ([]
   (js/parseInt (.-selectionStart (.getElementById js/document "editor")) 10))
  ([new-pos]
   (let [el (aget (.-childNodes ($editor)) 0)
         range (.createRange js/document)
         sel (.getSelection js/document)]
     (when el
       (.setStart range el new-pos)
       (.collapse range true)
       (.removeAllRanges sel)
       (.addRange sel range)))))

;; Document processing
(defn set-pending! [state]
  (swap! app-state assoc-in [:local-document :pending?] state))

(defn buf-append [operation]
  (swap! app-state update-in [:local-document :buffer] conj operation))

(defn apply! [operation]
  (swap! app-state update-in [:local-document :text] document/apply-ops operation))

(defn flush-buffer! []
  (set-pending! false)
  (reduce composers/compose (get-in @app-state [:local-document :buffer])))

(defn insert! [operation & {:keys [local?] :or {local? false}}]
  (if (get-in @app-state [:local-document :pending?])
    (buf-append operation)
    (set-pending! true))
  (apply! operation))

(defn insert-operation [char position]
  (let [current-text (get-in @app-state [:local-document :text])
        remaining (- (count current-text) position)]
    (cond-> []
      (pos? position)  (into (defops ::operations/ret position))
      true             (into (defops ::operations/ins char))
      (pos? remaining) (into (defops ::operations/ret remaining)))))

;; Build out UI
(defn key-handler [event]
  (.preventDefault event)
  (when-not (some #(= (.-keyCode event) %) [8 37 38 39 40])
    (let [char (.fromCharCode js/String (.-which event))]
      (insert! (insert-operation char (caret-position))))))

(defn greeting []
  [:div
   [:h1 (:text @app-state)]
   [:p (str "Buffering? " (get-in @app-state [:local-document :pending?]))]
   [:textarea {:id "editor"
               :on-key-press #(key-handler %)
               :value (get-in @app-state [:local-document :text])}]])


;; Set up some initialization
(defn fetch-remote-document []
  (GET "/document.json"
      :response-format :json
      :keywords? true
      :handler (fn [{:keys [initial-text last-id]}]
                 (swap! app-state assoc-in [:remote-document :last-id] last-id)
                 (swap! app-state assoc-in [:local-document :text] initial-text))))

(defn run []
  (reagent/render [greeting] (js/document.getElementById "app")))

(defn init []
  (fetch-remote-document)
  (run))

;; Kick things off
(init)
