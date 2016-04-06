(ns plaintext.server
  (:use [org.httpkit.server :only [run-server]])
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.json :refer [wrap-json-response]]
            [onelog.core :as log]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [othello.store :as store]
            [othello.operations :as op :refer (defops)]
            [clojure.pprint :as pprint])
  (:gen-class))

(defn pformat [& args]
  (with-out-str
    (apply pprint/pprint args)))

(defn make-uuid []
  (java.util.UUID/randomUUID))

(defn init-state []
  (let [start-id (make-uuid)
        initial-operations (-> (store/operation-list)
                               (conj (store/operation (defops ::op/ins "!") :id start-id)))]
    {:last-id start-id
     :operations initial-operations}))

(defonce document-state (atom (init-state)))
(defn reset-state! [] (reset! document-state (init-state)))

(defn insert! [{:keys [operations parent-id client-id]}]
  (let [unique-id (make-uuid)]
    (as-> operations $
        (store/operation $ :parent-id parent-id :id unique-id)
        (swap! document-state #(-> %
                                   (update :operations conj $)
                                   (assoc :last-id unique-id)))
        {:operations (get (:operations $) unique-id)
         :parent-id parent-id
         :id unique-id
         :client-id client-id})))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

(defroutes routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (GET "/document.json" []
      {:status 200
       :body (let [{:keys [last-id operations]} @document-state]
               {:last-id last-id :initial-text (store/as-string operations)})})
  (GET "/" []
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (resources "/"))

(defn broadcast! [event data]
  (log/info "BROADCASTING")
  (doseq [uid (:any @connected-uids)]
    (log/info "broadcast!" uid event data)
    (chsk-send! uid [event data])))

(defn event-msg-handler [{:as ev-msg :keys [id ?data]}]
  (log/info "WS RECV" ev-msg)
  (when (= id :document/some-id)
   (broadcast! :editor/operation (insert! ?data))))

(def http-handler
  (-> routes
      (wrap-defaults site-defaults)
      wrap-with-logger
      wrap-gzip
      wrap-json-response
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(defonce system (atom {}))

(defn start! [handler & port]
  (let [port (Integer. (or port (env :port) 8080))]
    (log/info "Initializing Sente router")
    (sente/start-server-chsk-router! ch-chsk event-msg-handler)
    (log/info "Starting server")
    (swap! system assoc :server
           (run-server handler {:port port :join? false}))))

(defn stop! []
  (let [server (get @system :server)]
    (server)))

(defn -main [& [port]]
  (start! http-handler port))
