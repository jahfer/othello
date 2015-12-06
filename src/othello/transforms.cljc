(ns othello.transforms
  (:require #?(:clj [clojure.core.match :refer [match]]
               :cljs [cljs.core.match :refer-macros [match]])
            [othello.operations :as o]))

(defn retain [value]
  [(o/->Op ::o/ret value) (o/->Op ::o/ret value)])

(defn dec-val [coll val]
  (update-in (vec coll) [0 :val] #(- % val)))

(defn retain-ops [ops1 ops2]
  (let [val1 (-> ops1 first :val)
        val2 (-> ops2 first :val)]
    (cond
     (> val1 val2) [(dec-val ops1 val2) (rest ops2)         (retain val2)]
     (= val1 val2) [(rest ops1)         (rest ops2)         (retain val2)]
     :else         [(rest ops1)         (dec-val ops2 val1) (retain val1)])))

(defn compress [ops]
  (with-meta (reduce (fn [acc op]
                       (if (and (= ::o/ret (:type op)) (zero? (:val op)))
                         acc
                         (if (every? o/retain? (list op (peek acc)))
                           (update-in acc [(-> acc count dec) :val] #(+ % (:val op)))
                           (conj acc op))))
                     [] ops) (meta ops)))

(defmulti transform-ops
  (fn [ops1 ops2 _]
    (let [first-type  (-> ops1 first (:type ::o/empty))
          second-type (-> ops2 first (:type ::o/empty))]
      [first-type second-type])))

(defmethod transform-ops [::o/ins ::o/operation] [ops1 ops2 ops']
  [(rest ops1) ops2 [(conj (first ops') (first ops1))
                     (conj (second ops') (o/->Op ::o/ret 1))]])

(defmethod transform-ops [::o/operation ::o/ins] [ops1 ops2 ops']
  [ops1 (rest ops2) [(conj (first ops')  (o/->Op ::o/ret 1))
                     (conj (second ops') (first ops2))]])

(defmethod transform-ops [::o/ret ::o/ret] [ops1 ops2 ops']
  (let [[ops1 ops2 result] (retain-ops ops1 ops2)]
    [ops1 ops2 (map conj ops' result)]))

(defmethod transform-ops [::o/del ::o/del] [ops1 ops2 ops']
  (let [val1 (:val (first ops1))
        val2 (:val (first ops2))]
    (cond
      (> val1 val2) [(dec-val ops1 val2) (rest ops2) ops']
      (= val1 val2) [(rest ops1) (rest ops2) ops']
      :else [(rest ops1) (dec-val ops2 val1) ops'])))

(defmethod transform-ops [::o/del ::o/ret] [ops1 ops2 ops']
  (let [val1 (:val (first ops1))
        val2 (:val (first ops2))]
    (cond
      (> val1 val2) [(dec-val ops1 val2) (rest ops2) (map conj ops' (first ops2))]
      (= val1 val2) [(rest ops1) (rest ops2) [(conj (first ops') (first ops1)) (second ops')]]
      :else [(rest ops1) (dec-val ops2 val1) [(conj (first ops') (first ops1)) (second ops')]])))

(defmethod transform-ops [::o/ret ::o/del] [ops1 ops2 ops']
  (let [val1 (:val (first ops1))
        val2 (:val (first ops2))]
    (cond
      (> val1 val2) [(dec-val ops1 val2) (rest ops2) [(first ops') (conj (second ops') (first ops2))]]
      (= val1 val2) [(rest ops1) (rest ops2) [(first ops') (conj (second ops') (first ops2))]]
      :else [(rest ops1) (dec-val ops2 val1) [(first ops') (conj (second ops') (o/->Op ::o/del val1))]])))

(prefer-method transform-ops [::o/ins ::o/operation] [::o/operation ::o/ins])

(defn transform [a b]
  (loop [ops1 a, ops2 b, ops' [[] []]]
    (if (or (seq ops1) (seq ops2))
      (let [[ops1 ops2 ops'] (transform-ops ops1 ops2 ops')]
        (recur ops1 ops2 ops'))
      (map compress ops'))))

(defn simplify [ops]
  (let [first-op (first ops)]
    (case (count ops)
      1 first-op
      2 (cond
          (o/retain? first-op) (second ops)
          (o/retain? (second ops)) first-op)
      3 (when (every? o/retain? (list first-op (peek ops)))
          (second ops))
      :else nil)))
