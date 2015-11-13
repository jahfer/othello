(ns othello.container-test
  (:require [othello.othello    :as othello]
            [othello.operations :as o]
            #?(:clj  [clojure.test :as t :refer (is deftest testing)]
               :cljs [cljs.test    :as t :refer-macros [is deftest testing]])))

(deftest build-container
  (testing "#build-container returns a map of :history and :document"
    (let [container (othello/build-container)]
      (is (= [:history :document] (vec (keys container)))))))

(deftest append-test
  (testing "#append! applies an operation to an empty container"
    (let [container (othello/build-container)]
      (othello/append! container (o/oplist ::o/ins "a"))
      (is (= "a" (othello/read-text container)))))
  (testing "#append! applies an operation on top of an existing history"
    (let [container (othello/build-container)]
      (othello/append! container (o/oplist ::o/ins "a"))
      (othello/append! container (o/oplist ::o/ret 1 ::o/ins "c" ::o/ins "k"))
      (is (= "ack" (othello/read-text container))))))
