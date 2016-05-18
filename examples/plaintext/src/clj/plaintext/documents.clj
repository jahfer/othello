(ns plaintext.documents
  (:require [onelog.core :as log]
            [othello.store :as store]
            [othello.operations :as op :refer (defops)]))

(defn- make-uuid [] (java.util.UUID/randomUUID))

(defn- init-state []
  (let [start-id (make-uuid)
        initial-operations (-> (store/operation-list)
                               (conj (store/operation (defops ::op/ins "!") :id start-id))
                               )]
    {:last-id start-id :operations initial-operations}))

(defonce document-state (atom (init-state)))

(defn- insert-operation [parent-id operations]
  (let [unique-id (make-uuid)
        op (store/operation operations :parent-id parent-id :id unique-id)]
    (swap! document-state update :operations conj op)
    unique-id))

(defn insert! [{:keys [parent-id operations] :as options}]
  (log/error options)
  (let [id (insert-operation parent-id operations)
        op-xf (get (:operations @document-state) id)]
    (swap! document-state assoc :last-id id)
    (merge options {:id id :operations op-xf})))

(defn serialize [] @document-state)
(defn init [] (reset! document-state (init-state)))
