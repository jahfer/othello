(ns othello.lib.test-check-helper
  (:require #+clj [clojure.test.check :as tc]
            #+cljs [cljs.test.check :as tc]
            #+clj [clojure.test.check.generators :as gen]
            #+cljs [cljs.test.check.generators :as gen]
            #+clj [clojure.core.match :refer [match]]
            #+cljs [cljs.core.match :refer-macros [match]]
            [othello.operations :as o :refer [oplist ->Op]]
            [othello.transforms :as transforms :refer [compress]]))

(defn oplen [op]
  (match [op]
         [{:type ::o/ret :val v}] v
         [{:type ::o/del :val v}] v
         :else 0))

(def ins-op-gen
  (gen/fmap (partial apply ->Op)
            (gen/tuple (gen/return ::o/ins)
                       gen/char-alpha)))

(def del-or-ret-op-gen
  (gen/fmap (partial apply ->Op)
            (gen/tuple (gen/elements [::o/ret ::o/del])
                       (gen/such-that #(not= % 0) gen/pos-int))))

(def op-gen
  (gen/one-of [ins-op-gen del-or-ret-op-gen]))

(def oplist-gen
  (gen/not-empty (gen/vector op-gen)))

(def oplist-pair-gen
  (gen/fmap
   (fn [[list-a list-b :as lists]]
     (let [diff (- (reduce + (map oplen list-a)) (reduce + (map oplen list-b)))]
       (cond
         (= 0 diff)  (map compress lists)
         (pos? diff) (map compress [list-a (conj list-b (->Op ::o/ret diff))])
         (neg? diff) (map compress [list-b (conj list-a (->Op ::o/ret (- diff)))]))))
   (gen/tuple oplist-gen oplist-gen)))
