(ns othello.operations)

(defrecord Op [type val])

#+cljs (enable-console-print!)

(derive ::ins ::operation)
(derive ::del ::operation)
(derive ::ret ::operation)
(derive ::empty ::operation)

(defn oplist [& operations]
  (mapv #(apply ->Op %) (partition 2 operations)))

#+cljs (defn ^:export oplistJS [arr]
         (let [coll (js->clj arr :keywordize-keys true)
               ops (flatten (map (fn [op]
                               (cond
                                 (contains? op :retain) [::ret (:retain op)]
                                 (contains? op :insert) [::ins (:insert op)]
                                 (contains? op :delete) [::del (:delete op)]))
                             coll))]
           (apply oplist ops)))

#+cljs (defn ^:export asJS [coll]
         (clj->js (map (fn [op]
                         (let [key (condp = (:type op)
                                     ::ret "retain"
                                     ::ins "insert"
                                     ::del "delete")]
                           {key (:val op)})) coll)))

(defn print-ops [operations]
  (pr-str (mapcat #(list (:type %) (:val %)) operations)))

(defn conj-ops [op-lists new-ops]
  (map conj op-lists new-ops))

(defn assoc-ops [new-ops ops]
  (conj-ops ops new-ops))

(defn insert? [operation]
  (= ::ins (:type operation)))

(defn retain? [operation]
  (= ::ret (:type operation)))

(defn delete? [operation]
  (= ::del (:type operation)))
