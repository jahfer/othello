(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/core.match          "0.3.0-alpha4"]
                  [adzerk/boot-test                "1.0.5"          :scope "test"]
                  [adzerk/boot-cljs                "1.7.170-2"      :scope "test"]
                  [crisptrutski/boot-cljs-test     "0.2.0-SNAPSHOT" :scope "test"]
                  [org.clojure/clojure             "1.7.0"]
                  [org.clojure/clojurescript       "1.7.145"]
                  [org.clojure/test.check          "0.8.2"          :scope "test"]])

(require '[adzerk.boot-cljs :refer [cljs]]
         '[crisptrutski.boot-cljs-test :refer [test-cljs]]
         '[adzerk.boot-test :refer [test]])

(task-options!
  pom {:project 'othello
       :version "0.2.0"}
  test-cljs {:js-env :phantom}
  test {})

(deftask testing
  "Profile setup for running tests."
  []
  (set-env! :source-paths #(conj % "test"))
  identity)

(deftask test-all []
  (comp (watch)
        (test-cljs)
        (test)))

(deftask test-only-clj []
  (comp (watch)
        (test)))

(deftask test-only-cljs []
  (comp (watch)
        (test-cljs)))
