(ns othello.store
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord OperationGroup [id parent-id operations])

(defn operation-group
  [operations & {:keys [id parent-id]
                 :or {id nil parent-id nil}}]
  (OperationGroup. id parent-id operations))

(defprotocol IHistory
  (operations-since-id [self id])
  (rebase [self new-tip]))

(deftype Document
    [^clojure.lang.PersistentArrayMap index
     ^clojure.lang.PersistentVector operations]
  IHistory
  (operations-since-id [self id]
    (subvec operations (inc (get index id))))
  (rebase [self {:keys [parent-id] :as new-tip}]
    (if (seq operations)
      (if (contains? index parent-id)
        (if-let [s (seq (operations-since-id self parent-id))]
          (->> (reduce composers/compose s)
               (transforms/transform (:operations new-tip))
               (first)
               (assoc new-tip :operations))
          new-tip)
        (throw (Exception. "Rejected operation. No common ancestor found.")))
      new-tip))
  clojure.lang.IPersistentCollection
  (seq [self] (seq operations))
  (cons [self x]
    (let [rebased (rebase self x)]
      (Document.
       (assoc index (:id rebased) (count operations))
       (conj operations (:operations rebased)))))
  (empty [self] (Document. {} []))
  (equiv [self o]
    (if (instance? Document o)
      (and (= index (.index o))
           (= operations (.operations o)))
      false)))

(defn as-string [^Document document]
  (some->> (seq document)
           (reduce composers/compose)
           (documents/apply-ops "")))

(defn document []
  (Document. {} []))

;; -----------

(comment
 (let [document (atom (document))]
   (swap! document conj operations)
   (as-text document)))
