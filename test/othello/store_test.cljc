(ns othello.store-test
  (:require [othello.store :as store]
            #?(:clj  [othello.operations :as o :refer (defops)]
               :cljs [othello.operations :as o :refer-macros [defops]])
            #?(:clj  [clojure.test :as t :refer (is deftest testing)]
               :cljs [cljs.test    :as t :refer-macros [is deftest testing]])))

(deftest conj-test
  (testing "#conj applies an operation to an empty container"
    (is (= "a" (-> (store/operation-list)
                   (conj (store/operation (defops ::o/ins "a")))
                   (store/as-string)))))

  (testing "#conj applies an operation on top of an existing history"
    (is (= "ack" (-> (store/operation-list)
                     (conj (store/operation (defops ::o/ins "a") :id 1))
                     (conj (store/operation (defops ::o/ret 1 ::o/ins "c" ::o/ins "k") :parent-id 1))
                     (store/as-string)))))

  (testing "#conj rebases an operation on changes since last parent"
    (is (= "goat" (-> (store/operation-list)
                      (conj (store/operation (defops ::o/ins "g") :id 1))
                      (conj (store/operation (defops ::o/ret 1 ::o/ins "o") :parent-id 1 :id 2))
                      (conj (store/operation (defops ::o/ret 2 ::o/ins "t") :parent-id 2))
                      (conj (store/operation (defops ::o/ret 2 ::o/ins "a") :parent-id 2))
                      (store/as-string)))))

  ;; (testing "#conj returns the history object"
  ;;   (let [container (atom (storeoperation-list))
  ;;         root (store/operation (o/oplist ::o/ins "c") :id 1)
  ;;         client-a (store/operation (o/oplist ::o/ret 1 ::o/ins "a") :parent-id (:id root))
  ;;         client-b (store/operation (o/oplist ::o/ret 1 ::o/ins "b") :parent-id (:id root))
  ;;         expected (store/->OperationGroup 3 1 (o/oplist ::o/ret 1 ::o/ins "b" ::o/ret 1))]
  ;;     (swap! container conj root)
  ;;     (swap! container conj client-a)
  ;;     (swap! container conj client-b)
  ;;     (is (= expected (last (.operations @container))))))
  )

(deftest as-string
  (testing "#as-string returns the expected text"
    (is (= "a" (-> (store/operation-list)
                   (conj (store/operation (defops ::o/ins "a")))
                   (store/as-string)))))

  (testing "#as-string is safe to call multiple times"
    (let [container (-> (store/operation-list)
                        (conj (store/operation (defops ::o/ins "1") :id 1))
                        (conj (store/operation (defops ::o/ret 1 ::o/ins "2") :parent-id 1)))]
      (is (= "12" (store/as-string container)))
      (is (= "12" (store/as-string container)))))

  (testing "#as-string returns nil for an empty container"
    (is (nil? (store/as-string (store/operation-list))))))
