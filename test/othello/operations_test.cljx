(ns othello.operations-test
  (:require #+clj [clojure.test :as t :refer (is deftest testing)]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing]]
            [othello.operations :as o]))

(def document "go")
(def op-tom (o/oplist ::o/ret 2 ::o/ins "a"))

(deftest oplist-test
  (testing "Produces a vector of Operations"
    (let [ops (o/oplist ::o/ret 5 ::o/ins "a" ::o/ret 3)]
      (is (= ops [(o/->Op ::o/ret 5) (o/->Op ::o/ins "a") (o/->Op ::o/ret 3)])))))
