(ns othello.documents-test
  (:require #+clj [clojure.test :as t :refer (is deftest testing)]
            #+cljs [cemerick.cljs.test :as t :refer-macros [is deftest testing]]
            [othello.operations :as o]
            [othello.documents :as documents]))

(def document "ram")

(def op-tom
  [(o/->Op ::o/ret 1)
   (o/->Op ::o/ins "o")
   (o/->Op ::o/ret 2)
   (o/->Op ::o/ins "!")])

(def op-jerry
  (o/oplist ::o/del 1 ::o/ins "R" ::o/ret 2))

(deftest apply-ins-test
  (testing "Applying an insert operation prepends the character to the document"
    (let [ins-op (o/->Op ::o/ins "g")
          out (documents/apply-ins ins-op document)]
      (is (= out "gram")))))

(deftest apply-ret-test
  (testing "Applying a retain operation splits the document at the retain count"
    (let [ret-op (o/->Op ::o/ret 2)
          {:keys [head tail]} (documents/apply-ret ret-op "hello")]
      (is (= head "he"))
      (is (= tail "llo")))))

(deftest apply-ops-test
  (testing "Applying a series of operations results in the correct end document"
    (let [result (documents/apply-ops document op-tom)]
      (is (= result "roam!"))))
  (testing "Applying a delete operation results in the correct end document"
    (let [result (documents/apply-ops document op-jerry)]
      (is (= result "Ram")))))

(deftest parse-ops-test
  (testing "#parse-ops returns a data structure of like nodes"
    (let [ops (o/oplist ::o/ins "a" ::o/ret 5 ::o/del 1 ::fooLink "http://google.com" ::o/ret 2 ::o/ins "b")]
      (is (= (documents/parse-ops ops)
             [{:nodeType ::documents/text :operations [(o/->Op ::o/ins "a")
                                                       (o/->Op ::o/ret 5)
                                                       (o/->Op ::o/del 1)]}
              {:nodeType ::fooLink :operations [(o/->Op ::fooLink "http://google.com")]}
              {:nodeType ::documents/text :operations [(o/->Op ::o/ret 2)
                                                       (o/->Op ::o/ins "b")]}])))))
