(ns othello.store
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord OperationGroup [id parent-id operations])

(defn- default-tag-fn []
  (let [global-id-counter (atom 0)]
    (fn [_] (swap! global-id-counter inc))))

(defprotocol History
  (operations-since-id [self id])
  (rebase [self new-tip]))

(deftype DefaultHistory [index operations tag-fn]
  History
  (operations-since-id [self id]
    (subvec operations (inc (get index id))))
  (rebase [self {:keys [parent-id] :as new-tip}]
    (if (seq operations)
      (if (contains? index parent-id)
        (if (seq (operations-since-id self parent-id))
          (->> (operations-since-id self parent-id)
               (reduce composers/compose)
               (transforms/transform (:operations new-tip))
               (first)
               (assoc new-tip :operations))
          new-tip)
        (println "Rejected operation. No common ancestor found."))
      new-tip))
  clojure.lang.IPersistentCollection
  (seq [self] (if (seq operations) self nil))
  (cons [self x]
    (let [rebased (update (rebase self x) :id tag-fn)]
      (DefaultHistory.
       (assoc (.index self) (:id rebased) (count operations))
       (conj (.operations self) (:operations rebased))
       tag-fn)))
  (empty [self] (DefaultHistory. {} [] tag-fn))
  (equiv [self o]
    (if (instance? DefaultHistory o)
      (and (= index (.index o))
           (= operations (.operations o)))
      false)))

(defprotocol Container
  (read-text [self])
  (append! [self operations]))

(defrecord DefaultContainer [history]
  Container
  (read-text [self]
    (let [history' (.operations @history)
          operations (if (seq history')
                       (reduce composers/compose history')
                       (first history'))]
      (documents/apply-ops "" operations)))
  ;; TODO: Need to return the rebased object with id/parent-id/operations
  (append! [self operations]
    (swap! history conj operations)))

(defn build-default-history []
  (DefaultHistory. {} [] (default-tag-fn)))

(defn build-container []
  (->DefaultContainer (atom (build-default-history))))

;; -----------

(comment
 (let [store (build-container)]
   (append! store operation)
   (read-text store)
   @store))
