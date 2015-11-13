(ns othello.othello
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

;; relying on meta doesn't work when flattening operations into history :(

(defn- append-to-history!
  [container operations]
  (swap! (:history container) into operations)
  operations)

(defn- operations-since-id [id base]
  (take-while #(not= (:id (meta %)) id)))

(defn- descendent?
  [parent child]
  (let [parent-id (:parent-id (meta child))]
    (println parent-id)
    (some #(= parent-id %) (map #(:id (meta %)) parent))))

(defn read-history
  [container]
  (deref (:history container)))

(defn- read-document
  [container]
  (deref (:document container)))

(defn build-container
  []
  {:history (atom '()) :document (atom "")})

(defn read-text
  [container]
  (let [history (reverse (read-history container))
        operations (if (> (count history) 1)
                    (reduce composers/compose history)
                    history)]
    (swap! (:document container) documents/apply-ops operations)))

(defn rebase
  [from onto]
  (if (seq onto)
    (if (descendent? onto from)
      (let [from-metadata (meta from)
            parent-id (:parent-id from-metadata)]
        (->> (operations-since-id parent-id onto)
             (reduce composers/compose)
             (transforms/transform from)
             (first)
             #(with-meta % from-metadata)))
      (println "Rejected operation. Not parented on known history"))
    from))

(defn append!
  ([container operations]
   (->> container
     (read-history)
     (rebase operations)
     (append-to-history! container)))
  ([container operations f]
   (apply f (append! container operations))))

;; -----------

(comment
 (let [container (build-container)]
   (append! container operation #(persist-to-db! %))
   (read-text container)))
