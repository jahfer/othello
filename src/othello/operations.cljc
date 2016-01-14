(ns othello.operations)

(defrecord Op [type val])

#?(:cljs (enable-console-print!))

(derive ::ins ::operation)
(derive ::del ::operation)
(derive ::ret ::operation)
(derive ::empty ::operation)

(defn oplist [& operations]
  (mapv #(apply ->Op %) (partition 2 operations)))

(defmacro defops [& operations]
  (mapv #(apply ->Op %) (partition 2 operations)))

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
