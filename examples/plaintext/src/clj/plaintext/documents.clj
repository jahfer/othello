(ns plaintext.documents
  (:require [othello.store :as store]
            [othello.operations :as op :refer (defops)]))

(defn- make-uuid []
  (java.util.UUID/randomUUID))

(defn- init-state []
  (let [start-id (make-uuid)
        initial-operations (-> (store/operation-list)
                               (conj (store/operation (defops ::op/ins "!") :id start-id)))]
    {:last-id start-id
     :operations initial-operations}))

(defonce document-state (atom (init-state)))

(defn serialize [] @document-state)
(defn reset! [] (reset! document-state (init-state)))

(defn- insert-operation [operations parent-id]
  (let [unique-id (make-uuid)
        op (store/operation operations :parent-id parent-id :id unique-id)]
    (swap! document-state (fn [state]
                            (update state :operations conj op)
                            (assoc state :last-id unique-id)))
    unique-id))

(defn insert! [{:keys [parent-id operations] :as options}]
  (let [id (insert-operation parent-id operations)
        op-xf (get (:operations @document-state) id)]
    (merge options {:operations op-xf})))
