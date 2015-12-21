(ns othello.store
  (:require [othello.composers  :as composers]
            [othello.transforms :as transforms]
            [othello.documents  :as documents]))

(defrecord OperationGroup [id parent-id operations])

(defn operation-group
  [operations & {:keys [id parent-id]
                 :or {id nil parent-id nil}}]
  (OperationGroup. id parent-id operations))

(defn- default-tag-fn []
  (let [global-id-counter (atom 0)]
    (fn [_] (swap! global-id-counter inc))))

(defprotocol IHistory
  (operations-since-id [self id])
  (rebase [self new-tip]))

(deftype Document [index operations tag-fn]
  IHistory
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
  (seq [self] (seq operations))
  (cons [self x]
    (let [rebased (update (rebase self x) :id tag-fn)]
      (Document.
       (assoc (.index self) (:id rebased) (count operations))
       (conj (.operations self) (:operations rebased))
       tag-fn)))
  (empty [self] (Document. {} [] tag-fn))
  (equiv [self o]
    (if (instance? Document o)
      (and (= index (.index o))
           (= operations (.operations o)))
      false)))

(defn as-string [^Document document]
  (when-let [s (seq document)]
    (documents/apply-ops "" (reduce composers/compose s))))

(defn build-document []
  (Document. {} [] (default-tag-fn)))

;; -----------

(comment
 (let [document (atom (build-document))]
   (swap! document conj operations)
   (as-text document)))
