(ns othello.store-test
  (:require [othello.store :as store]
            [othello.operations :as o]
            #?(:clj  [clojure.test :as t :refer (is deftest testing)]
               :cljs [cljs.test    :as t :refer-macros [is deftest testing]])))

(deftest append-test
  (testing "#conj applies an operation to an empty container"
    (let [container  (atom (store/build-document))
          operations (store/->OperationGroup nil nil (o/oplist ::o/ins "a"))]
      (swap! container conj operations)
      (is (= "a" (store/as-string @container)))))

  (testing "#conj applies an operation on top of an existing history"
    (let [container (atom (store/build-document))
          op1 (store/operation-group (o/oplist ::o/ins "a"))
          op2 (store/operation-group (o/oplist ::o/ret 1 ::o/ins "c" ::o/ins "k") :parent-id 1)]
      (swap! container conj op1)
      (swap! container conj op2)
      (is (= "ack" (store/as-string @container)))))

  (testing "#conj rebases an operation on changes since last parent"
    (let [container       (atom (store/build-document))
          root            (store/operation-group (o/oplist ::o/ins "g") :id 1)
          common-ancestor (store/operation-group (o/oplist ::o/ret 1 ::o/ins "o") :parent-id (:id root) :id 2)
          client-a        (store/operation-group (o/oplist ::o/ret 2 ::o/ins "t") :parent-id (:id common-ancestor))
          client-b        (store/operation-group (o/oplist ::o/ret 2 ::o/ins "a") :parent-id (:id common-ancestor))]
      (swap! container conj root)
      (swap! container conj common-ancestor)
      (swap! container conj client-a)
      (swap! container conj client-b)
      (is (= "goat" (store/as-string @container)))))

  (testing "#conj returns the history object"
    (let [container (atom (store/build-document))
          root (store/operation-group (o/oplist ::o/ins "c") :id 1)
          client-a (store/operation-group (o/oplist ::o/ret 1 ::o/ins "a") :parent-id (:id root))
          client-b (store/operation-group (o/oplist ::o/ret 1 ::o/ins "b") :parent-id (:id root))
          ;; expected (store/->OperationGroup 3 1 (o/oplist ::o/ret 1 ::o/ins "b" ::o/ret 1))
          expected (o/oplist ::o/ret 1 ::o/ins "b" ::o/ret 1)]
      (swap! container conj root)
      (swap! container conj client-a)
      (swap! container conj client-b)
      (is (= expected (last (.operations @container)))))))

(deftest as-string
  (testing "#as-string returns the expected text"
    (let [container (atom (store/build-document))]
      (swap! container conj (store/operation-group (o/oplist ::o/ins "a")))
      (is (= "a" (store/as-string @container)))))

  (testing "#as-string is safe to call multiple times"
    (let [container (atom (store/build-document))]
      (swap! container conj (store/operation-group (o/oplist ::o/ins "1") :id 1))
      (swap! container conj (store/operation-group (o/oplist ::o/ret 1 ::o/ins "2") :parent-id 1))
      (is (= "12" (store/as-string @container)))
      (is (= "12" (store/as-string @container)))))

  (testing "#as-string returns nil for an empty container"
    (let [container (atom (store/build-document))]
      (is (nil? (store/as-string @container))))))
