(ns othello.container-test
  (:require [othello.othello    :as othello]
            [othello.operations :as o]
            #?(:clj  [clojure.test :as t :refer (is deftest testing)]
               :cljs [cljs.test    :as t :refer-macros [is deftest testing]])))

(deftest build-container
  (testing "#build-container returns a map of :history and :document"
    (let [container (othello/build-container)]
      (is (= [:history :document :tag-fn] (vec (keys container)))))))

(deftest append-test
  (testing "#append! applies an operation to an empty container"
    (let [container  (othello/build-container)
          operations (othello/->OperationGroup nil nil (o/oplist ::o/ins "a"))]
      (othello/append! container operations)
      (is (= "a" (othello/read-text container)))))

  (testing "#append! applies an operation on top of an existing history"
    (let [container (othello/build-container)
          op1 (othello/->OperationGroup nil nil (o/oplist ::o/ins "a"))
          op2 (othello/->OperationGroup nil 1 (o/oplist ::o/ret 1 ::o/ins "c" ::o/ins "k"))]
      (othello/append! container op1)
      (othello/append! container op2)
      (is (= "ack" (othello/read-text container)))))
  ;
  (testing "#append! rebases an operation on changes since last parent"
    (let [container       (othello/build-container)
          root            (othello/->OperationGroup 1   nil                   (o/oplist ::o/ins "g"))
          common-ancestor (othello/->OperationGroup 2   (:id root)            (o/oplist ::o/ret 1 ::o/ins "o"))
          client-a        (othello/->OperationGroup nil (:id common-ancestor) (o/oplist ::o/ret 2 ::o/ins "t"))
          client-b        (othello/->OperationGroup nil (:id common-ancestor) (o/oplist ::o/ret 2 ::o/ins "a"))]
      (othello/append! container root)
      (othello/append! container common-ancestor)
      (othello/append! container client-a)
      (othello/append! container client-b)
      (is (= "goat" (othello/read-text container))))))
