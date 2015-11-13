(ns othello.composers-test
  (:use [othello.composers :only [compose]])
  (:require #?(:clj  [clojure.test :as t :refer (is deftest testing)]
               :cljs [cljs.test :as t :refer-macros [is deftest testing]])
            [othello.operations :as o :refer [oplist]]))

(deftest basic-compose-test
  (testing ":del _"
    (let [comp (compose (oplist ::o/del 1) (oplist ::o/ins "a"))]
      (is (= comp (oplist ::o/del 1 ::o/ins "a")))))
  (testing "_ ::o/ins"
   (let [comp (compose (oplist ::o/ret 1) (oplist ::o/ins "a" ::o/ret 1))]
     (is (= comp (oplist ::o/ins "a" ::o/ret 1)))))
  (testing "::o/ret ::o/ret"
    (let [comp (compose (oplist ::o/ret 1 ::o/ret 1) (oplist ::o/ret 2))]
      (is (= comp (oplist ::o/ret 1 ::o/ret 1)))))
  (testing "::o/ins ::o/ret"
    (let [comp (compose (oplist ::o/ins "a" ::o/ret 1) (oplist ::o/ret 2 ::o/ins "b"))]
      (is (= comp (oplist ::o/ins "a" ::o/ret 1 ::o/ins "b")))))
  (testing "::o/ins ::o/del"
    (let [comp (compose (oplist ::o/ins "a" ::o/ret 1) (oplist ::o/del 1 ::o/ret 1))]
      (is (= comp (oplist ::o/ret 1)))))
  (testing "::o/ret ::o/del"
    (let [comp (compose (oplist ::o/ret 1) (oplist ::o/del 1))]
      (is (= comp (oplist ::o/del 1))))))

(deftest compose-test
  (testing "Composing two lists of operations results in a single list of all combined operations"
    (let [comp (compose (oplist ::o/ret 1 ::o/ins "o") (oplist ::o/ret 2 ::o/ins "t"))]
      (is (= comp (oplist ::o/ret 1 ::o/ins "o" ::o/ins "t")))))
  (testing "Composing two delete actions"
    (let [comp (compose (oplist ::o/ret 3 ::o/del 1) (oplist ::o/ret 2 ::o/del 1))]
      (is (= comp (oplist ::o/ret 2 ::o/del 1 ::o/del 1))))))



(defmethod othello.composers/compose-ops [::img ::o/ret] [ops1 ops2 out]
  (let [ops2 (if (= 1 (get-in (vec ops2) [0 :val]))
               (rest ops2)
               (update-in (vec ops2) [0 :val] dec))]
    [(rest ops1) ops2 (conj out (first ops1))]))

(defmethod othello.composers/compose-ops [::img ::o/del] [ops1 ops2 out]
  [(rest ops1) (rest ops2) out])

(derive ::img :othello.operations/hello)

(deftest wacky-test
  (testing "retain over custom operation"
    (let [comp (compose (oplist ::o/ret 1 ::img "http://google.com/logo.png")
                        (oplist ::o/ret 2 ::o/ins "b"))]
      (is (= comp (oplist ::o/ret 1 ::img "http://google.com/logo.png" ::o/ins "b")))))
  (testing "deleting a custom operation"
    (let [comp (compose (oplist ::o/ret 1 ::img "http://google.com/logo.png")
                        (oplist ::o/ret 1 ::o/del 1))]
      (is (= comp (oplist ::o/ret 1))))))
