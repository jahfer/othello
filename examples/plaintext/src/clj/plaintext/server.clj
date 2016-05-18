(ns plaintext.server
  (:use [org.httpkit.server :only [run-server]])
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-response]]
            [onelog.core :as log]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [plaintext.documents :as doc]
            [othello.store :as store])
  (:gen-class))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {})]
  (def ring-ajax-post                ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv)
  (def chsk-send!                    send-fn)
  (def connected-uids                connected-uids))

(defn broadcast! [event data]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid [event data])))

(defn event-msg-handler [{:as ev-msg :keys [id ?data]}]
  (when (= id :document/some-id)
    (let [inserted (doc/insert! ?data)]
      (broadcast! :editor/operation inserted))))

(defroutes routes
  (GET  "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (GET "/document.json" []
      {:status 200
       :body (let [{:keys [last-id operations]} (doc/serialize)]
               {:last-id last-id :initial-text (store/as-string operations)})})
  (GET "/" []
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (resources "/"))

(defonce system (atom {}))

(def http-handler
  (-> routes
      (wrap-defaults site-defaults)
      wrap-json-response
      ring.middleware.keyword-params/wrap-keyword-params
      ring.middleware.params/wrap-params))

(defn start! [handler & port]
  (let [port (Integer. (or port (env :port) 8080))]
    (sente/start-server-chsk-router! ch-chsk event-msg-handler)
    (swap! system assoc :server
           (run-server handler {:port port :join? false}))))

(defn stop! []
  (let [server (get @system :server)]
    (server)))

(defn reset-state! []
  (doc/init)
  (broadcast! :browser/refresh true))

(defn -main [& [port]]
  (start! http-handler port))
