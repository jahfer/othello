(ns othello.store
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord Operation [id parent-id operations])

(defn operation
  [operations & {:keys [id parent-id]
                 :or {id nil parent-id nil}}]
  (Operation. id parent-id operations))

(defprotocol OTCollection
  (take-since [self id])
  (rebase [self new-tip]))

(declare otl-conj)

#?(:clj (deftype OperationalTransformList
            [^clojure.lang.PersistentArrayMap index
             ^clojure.lang.PersistentVector operations]
          clojure.lang.ILookup
          (valAt [self id]
            (some->> id (get (.-index self)) (get (.-operations self))))
          clojure.lang.IPersistentCollection
          (seq [self] (seq operations))
          (cons [self x] (otl-conj self x))
          (empty [self] (OperationalTransformList. {} [])))
   :cljs (deftype OperationalTransformList [index operations]
           ILookup
           (-lookup [self id]
            (some->> id (get (.-index self)) (get (.-operations self))))
           ISeqable
           (-seq [self] (seq operations))
           ICollection
           (-conj [self x] (otl-conj self x))
           IEmptyableCollection
           (-empty [self] (OperationalTransformList. {} []))))

(defn- otl-conj [^OperationalTransformList coll x]
  (let [rebased (rebase coll x)]
    (OperationalTransformList.
     (assoc (.-index coll) (:id rebased) (count (.-operations coll)))
     (conj (.-operations coll) (:operations rebased)))))

(extend-type OperationalTransformList
  OTCollection
  (take-since [self id]
    (subvec (.-operations self) (inc (get (.-index self) id))))
  (rebase [self {:keys [parent-id] :as new-tip}]
    (if (seq (.-operations self))
      (if (contains? (.-index self) parent-id)
        (if-let [s (seq (take-since self parent-id))]
          (->> (reduce composers/compose s)
               (transforms/transform (:operations new-tip))
               (first)
               (assoc new-tip :operations))
          new-tip)
        #?(:clj (throw (Exception. "Rejected operation. No common ancestor found."))
           :cljs (println "Rejected operation. No common ancestor found.")))
      new-tip)))

(defn as-string [^OperationalTransformList document]
  (some->> (seq document)
           (reduce composers/compose)
           (documents/apply-ops "")))

(defn operation-list []
  (empty (->OperationalTransformList nil nil)))

;; -----------

(comment
  (-> (document) (conj op1) (conj op3) (conj op2) (as-text))

  (let [document (atom (document))]
    (swap! document conj operations)
    (as-text document)))
