(ns othello.store
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord OperationGroup [id parent-id operations])

(defn- append-to-history!
  [container new-operations]
  (swap! (:history container) (fn [{:keys [operations index] :as history}]
                                (-> history
                                    (update-in [:operations] conj (:operations new-operations))
                                    (update-in [:index] assoc (:id new-operations) (count operations)))))
  new-operations)

(defn read-history
  [container]
  (deref (:history container)))

(defn- read-document
  [container]
  (deref (:document container)))

(defn- apply-tag [container operations]
  (update operations :id (:tag-fn container)))

(defn- default-tag-fn []
  (let [global-id-counter (atom 0)]
    (fn [_] (swap! global-id-counter inc))))

(defn build-container
  [& {:keys [tag-fn] :or {tag-fn (default-tag-fn)}}]
  {:history (atom {:index {} :operations []})
   :document (atom "")
   :tag-fn tag-fn})

(defn read-text
  [container]
  (let [history (:operations (read-history container))
        operations (if (> (count history) 1)
                    (reduce composers/compose history)
                    (first history))]
    (swap! (:document container) documents/apply-ops operations)))

(defn rebase
  [{:keys [parent-id operations] :as from} {:keys [index] :as history}]
  (if (seq (:operations history))
    (if (contains? index parent-id)
      (let [history-ops (:operations history)
            missed-operations (->> parent-id (get index) inc (subvec history-ops))]
        (if (seq missed-operations)
          (->> missed-operations
            (reduce composers/compose)
            (transforms/transform operations)
            (first)
            (assoc from :operations))
          from))
      (println "Rejected operation. No common ancestor found."))
    from))

(defn append!
  "Applies an OperationGroup to an existing container."
  [container operations]
  (->> container
    (read-history)
    (rebase operations)
    (apply-tag container)
    (append-to-history! container)))

;; -----------

(comment
 (let [store (build-container)]
   (append! store operation)
   (read-text store)))
