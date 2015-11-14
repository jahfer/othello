(ns othello.othello
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord OperationGroup [id parent-id operations])

(defn- append-to-history!
  [container operations]
  (swap! (:history container) conj operations)
  operations)

(defn- operations-since-id [id base]
  (map :operations (take-while #(not= id (:id %)) base)))

(defn- descendent?
  [maybe-parents {:keys [parent-id] :as child}]
  (some #(= parent-id %) (map :id maybe-parents)))

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
  {:history (atom '()) :document (atom "") :tag-fn tag-fn})

(defn read-text
  [container]
  (let [history (reverse (map :operations (read-history container)))
        operations (if (> (count history) 1)
                    (reduce composers/compose history)
                    (first history))]
    (swap! (:document container) documents/apply-ops operations)))

(defn rebase
  [{:keys [parent-id operations] :as from} onto]
  (if (seq onto)
    (if (descendent? onto from)
      (let [missed-operations (operations-since-id parent-id onto)]
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
  "Applies an OperationGroup to an existing container. Takes f which generates a tag for the persisted operation."
  [container operations]
  (->> container
    (read-history)
    (rebase operations)
    (apply-tag container)
    (append-to-history! container)))

;; -----------

(comment
 (let [container (build-container)]
   (append! container operation)
   (read-text container)))
