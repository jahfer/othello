(ns othello.store
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord Operation [id parent-id operations])

(defn operation
  [operations & {:keys [id parent-id]
                 :or {id nil parent-id nil}}]
  (Operation. id parent-id operations))

(declare conj')

#?(:clj (deftype OperationalTransformList
            [^clojure.lang.PersistentArrayMap index
             ^clojure.lang.PersistentVector operations]
          clojure.lang.ILookup
          (valAt [self id]
            (some->> id (get (.-index self)) (get (.-operations self))))
          clojure.lang.IPersistentCollection
          (seq [self] (seq operations))
          (cons [self x] (conj' self x))
          (empty [self] (OperationalTransformList. {} []))
          Object
          (toString [_] (str "OperationalTransformList: " (pr-str index))))

   :cljs (deftype OperationalTransformList [index operations]
           ILookup
           (-lookup [self id]
            (some->> id (get (.-index self)) (get (.-operations self))))
           ISeqable
           (-seq [self] (seq operations))
           ICollection
           (-conj [self x] (conj' self x))
           IEmptyableCollection
           (-empty [self] (OperationalTransformList. {} []))
           Object 
           (toString [_] (str "OperationalTransformList: " (pr-str index)))))

(defn- take-since [^OperationalTransformList coll id]
  (subvec (.-operations coll) (inc (get (.-index coll) id))))

(defn- rebase [^OperationalTransformList coll {:keys [parent-id] :as new-tip}]
  (if (seq (.-operations coll))
    (if (contains? (.-index coll) parent-id)
      (if-let [s (seq (take-since coll parent-id))]
        (->> (reduce composers/compose s)
             (transforms/transform (:operations new-tip))
             (first)
             (assoc new-tip :operations))
        new-tip)
      #?(:clj (throw (ex-info "Rejected operation. No common ancestor found."
                              {:type ::illegal-operation :parent-id parent-id}))
         :cljs (println "Rejected operation. No common ancestor found.")))
    new-tip))

(defn- conj' [^OperationalTransformList coll x]
  (let [rebased (rebase coll x)]
    (OperationalTransformList.
     (assoc (.-index coll) (:id rebased) (count (.-operations coll)))
     (conj (.-operations coll) (:operations rebased)))))

(defn as-string [^OperationalTransformList coll]
  (some->> (seq coll)
           (reduce composers/compose)
           (documents/apply-ops "")))

(defn operation-list []
  (empty (->OperationalTransformList nil nil)))

;; -----------

(comment
  (-> (operation-list)
      (conj op1)
      (conj op3)
      (conj op2)
      (as-text))

  (let [document (atom (document))]
    (swap! document conj operations)
    (as-text document)))
