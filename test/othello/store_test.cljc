(ns othello.store-test
  (:require [othello.store :as store]
            [othello.operations :as o]
            #?(:clj  [clojure.test :as t :refer (is deftest testing)]
               :cljs [cljs.test    :as t :refer-macros [is deftest testing]])))

(deftest build-container
  (testing "#build-container returns a map of :history and :document"
    (let [container (store/build-container)]
      (is (= [:history :document :tag-fn] (vec (keys container)))))))

(deftest append-test
  (testing "#append! applies an operation to an empty container"
    (let [container  (store/build-container)
          operations (store/->OperationGroup nil nil (o/oplist ::o/ins "a"))]
      (store/append! container operations)
      (is (= "a" (store/read-text container)))))

  (testing "#append! applies an operation on top of an existing history"
    (let [container (store/build-container)
          op1 (store/->OperationGroup nil nil (o/oplist ::o/ins "a"))
          op2 (store/->OperationGroup nil 1 (o/oplist ::o/ret 1 ::o/ins "c" ::o/ins "k"))]
      (store/append! container op1)
      (store/append! container op2)
      (is (= "ack" (store/read-text container)))))

  (testing "#append! rebases an operation on changes since last parent"
    (let [container       (store/build-container)
          root            (store/->OperationGroup 1   nil                   (o/oplist ::o/ins "g"))
          common-ancestor (store/->OperationGroup 2   (:id root)            (o/oplist ::o/ret 1 ::o/ins "o"))
          client-a        (store/->OperationGroup nil (:id common-ancestor) (o/oplist ::o/ret 2 ::o/ins "t"))
          client-b        (store/->OperationGroup nil (:id common-ancestor) (o/oplist ::o/ret 2 ::o/ins "a"))]
      (store/append! container root)
      (store/append! container common-ancestor)
      (store/append! container client-a)
      (store/append! container client-b)
      (is (= "goat" (store/read-text container))))))
