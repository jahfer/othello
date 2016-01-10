(ns othello.composers
  #?(:clj (:use[clojure.core.match :only (match)]))
  (:require [othello.operations :as o]
            [othello.transforms :as t]
            #?(:cljs [cljs.core.match :refer-macros [match]])))

#?(:cljs (enable-console-print!))

(defmulti compose-ops
  (fn [ops1 ops2 _]
    (let [first-type  (-> ops1 first (:type ::o/empty))
          second-type (-> ops2 first (:type ::o/empty))]
      [first-type second-type])))

(defmethod compose-ops [::o/ret ::o/ret] [ops1 ops2 out]
  (let [[ops1 ops2 [result]] (t/retain-ops ops1 ops2)]
    [ops1 ops2 (conj out result)]))

(defmethod compose-ops [::o/del ::o/operation] [ops1 ops2 out]
  [(rest ops1) ops2 (->> ops1 first (conj out))])

(defmethod compose-ops [::o/operation ::o/ins] [ops1 ops2 out]
  [ops1 (rest ops2) (->> ops2 first (conj out))])

(defmethod compose-ops [::o/ins ::o/ret] [ops1 ops2 out]
  (let [ops1 (vec ops1)
        ops2 (vec ops2)]
    (let [ops2 (if (= 1 (get-in ops2 [0 :val]))
                 (rest ops2)
                 (update-in ops2 [0 :val] dec))]
      [(rest ops1) ops2 (conj out (first ops1))])))

(defmethod compose-ops [::o/ins ::o/del] [ops1 ops2 out]
  [(rest ops1) (rest ops2) out])

(defmethod compose-ops [::o/ret ::o/del] [ops1 ops2 out]
  (let [del-op (first ops2)]
    [(rest ops1) (rest ops2) (conj out del-op)]))

(prefer-method compose-ops [::o/del ::o/operation] [::o/operation ::o/ins])

(defn compose [a b]
  (loop [ops1 a, ops2 b, composed []]
    (if (or (seq ops1) (seq ops2))
      (let [[ops1 ops2 composed] (compose-ops ops1 ops2 composed)]
        (recur ops1 ops2 composed))
      composed)))
