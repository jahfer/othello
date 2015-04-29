(ns othello.transforms-test
  (:use [othello.transforms :only [transform compress retain]])
  (:require #+clj [clojure.test :as t :refer (is deftest testing)]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing]]
            #+clj [clojure.test.check.properties :as prop]
            #+cljs [cljs.test.check.properties :as prop :refer-macros [for-all]]
            #+clj [clojure.test.check.clojure-test :refer [defspec]]
            #+cljs [cljs.test.check.cljs-test :refer-macros [defspec]]
            [othello.lib.test-check-helper :as tch]
            [othello.operations :as o :refer [oplist ->Op]]
            [othello.documents :as documents]))

(def document "go")

(def op-tom   (oplist ::o/ret 2 ::o/ins "a"))
(def op-jerry (oplist ::o/ret 2 ::o/ins "t"))
(def op-barry (oplist ::o/ret 1 ::o/del 1))

(def tom-document
  (str document "a"))
(def jerry-document
  (str document "t"))

(defn assert-transforms [doc a b expected-a' expected-b']
  "Asserts that transform return the expected values, and fulfills the commutative expectation"
  (let [doc-a (documents/apply-ops doc a)
        doc-b (documents/apply-ops doc b)
        [a' b'] (transform a b)]
    (is (= a' expected-a'))
    (is (= b' expected-b'))
    (is (= (documents/apply-ops doc-a b') (documents/apply-ops doc-b a')))))

(deftest testcheck-regression-tests
  (testing "multi-char delete + unequal-length retain regression"
    (let [a (oplist ::o/ret 1 ::o/ins "i" ::o/ret 2)
          b (oplist ::o/del 2 ::o/ret 1)
          expected-a' (oplist ::o/ins "i" ::o/ret 1)
          expected-b' (oplist ::o/del 1 ::o/ret 1 ::o/del 1 ::o/ret 1)]
      (assert-transforms "hya" a b expected-a' expected-b')))
  (testing "competing deletes of different length regression"
    (let [a (oplist ::o/del 2 ::o/ret 1)
          b (oplist ::o/del 1 ::o/ret 2)
          expected-a' (oplist ::o/del 1 ::o/ret 1)
          expected-b' (oplist ::o/ret 1)]
      (assert-transforms "hey" a b expected-a' expected-b'))))

(defspec transform-works-with-even-length-operations
  100
  (prop/for-all [[a b] tch/oplist-pair-gen]
                (let [doc (clojure.string/join (take (reduce + (map tch/oplen a)) (repeat "a")))
                      doc-a (documents/apply-ops doc a)
                      doc-b (documents/apply-ops doc b)
                      [a' b'] (transform a b)]
                  (= (documents/apply-ops doc-a b') (documents/apply-ops doc-b a')))))

(deftest retain-test
  (testing "retain will return a vec of two retain operations of the same value"
    (let [[a b] (retain 5)]
      (is (= a (->Op ::o/ret 5)))
      (is (= b (->Op ::o/ret 5))))))

(deftest transform-test
  (testing "Transforming two operations"
    (let [expected-a' (oplist ::o/ret 2 ::o/ins "a" ::o/ret 1)
          expected-b' (oplist ::o/ret 3 ::o/ins "t")]
            (assert-transforms "Hi" op-tom op-jerry expected-a' expected-b'))))

(deftest delete-test
  (testing "Transforming a delete operation with an insert operation"
    (let [expected-a' (oplist ::o/ret 1 ::o/ins "a")
          expected-b' (oplist ::o/ret 1 ::o/del 1 ::o/ret 1)]
      (assert-transforms "Hi" op-tom op-barry expected-a' expected-b')))

  (testing "Transforming an insert operation with a delete operation"
    (let [expected-a' (oplist ::o/ret 1 ::o/del 1 ::o/ret 1)
          expected-b' (oplist ::o/ret 1 ::o/ins "a")]
      (assert-transforms "Hi" op-barry op-tom expected-a' expected-b')))

  (testing "Tranforming two delete operations"
    (let [expected-a' (oplist ::o/ret 1)
          expected-b' (oplist ::o/ret 1)]
      (assert-transforms "Hi" op-barry op-barry expected-a' expected-b')))

  (testing "Transforming deletes at different points of the same document"
    (let [a (oplist ::o/ret 1 ::o/del 1 ::o/ret 2)
          b (oplist ::o/ret 2 ::o/del 1 ::o/ret 1)
          expected-a' (oplist ::o/ret 1 ::o/del 1 ::o/ret 1)
          expected-b' (oplist ::o/ret 1 ::o/del 1 ::o/ret 1)]
      (assert-transforms "Hi?!" a b expected-a' expected-b'))))

(deftest compress-test
  (testing "#compress will join all neighboring like items"
    (let [ops1 (oplist ::o/ret 2 ::o/ret 1 ::o/ins "a" ::o/ret 1 ::o/ret 3)
          ops2 (oplist ::o/ret 1 ::o/ret 1 ::o/ret 1 ::o/ret 1 ::o/ins "b")
          result1 (compress ops1)
          result2 (compress ops2)]
      (is (= result1 (oplist ::o/ret 3 ::o/ins "a" ::o/ret 4)))
      (is (= result2 (oplist ::o/ret 4 ::o/ins "b")))))
  (testing "#compress will remove retains of length 0"
    (is (= (oplist ::o/ins "a") (compress (oplist ::o/ret 0 ::o/ins "a" ::o/ret 0))))))


(defmethod othello.transforms/transform-ops [::img ::o/operation] [ops1 ops2 ops']
  [(rest ops1) ops2 [(conj (first ops') (first ops1))
                     (conj (first ops') (o/->Op ::o/ret 1))]])

(derive ::img :othello.operations/hello)

(deftest wacky-test
  (testing "yeah..."
    (let [a (oplist ::o/ret 1 ::img "http://google.com/logo.png")
          b (oplist ::o/ret 1 ::o/ins "b")
          expected-a' (oplist ::o/ret 1 ::img "http://google.com/logo.png" ::o/ret 1)
          expected-b' (oplist ::o/ret 2 ::o/ins "b")]
      (assert-transforms "a" a b expected-a' expected-b'))))
