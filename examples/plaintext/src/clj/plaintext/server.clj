(ns plaintext.server
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE defroutes]]
            [compojure.route :refer [resources]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.json :refer [wrap-json-response]]
            [environ.core :refer [env]]
            [ring.adapter.jetty :refer [run-jetty]]
            [othello.store :as store]
            [othello.operations :as op :refer (defops)])
  (:gen-class))

(def counter (atom 0))
(defn next-id [] (swap! counter inc'))

(defn initial-operations []
  (-> (store/operation-list)
      (conj (store/operation (defops ::op/ins "!") :id (next-id)))))

(defonce document-state
  (atom {:last-id 0 :operations (initial-operations)}))

(defroutes routes
  (GET "/document.json" []
      {:status 200
       :body (let [{:keys [last-id operations]} @document-state]
               {:last-id last-id :initial-text (store/as-string operations)})})
  (GET "/" []
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (io/input-stream (io/resource "public/index.html"))})
  (resources "/"))

(def http-handler
  (-> routes
      (wrap-defaults api-defaults)
      wrap-with-logger
      wrap-gzip
      wrap-json-response))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 10555))]
    (run-jetty http-handler {:port port :join? false})))
