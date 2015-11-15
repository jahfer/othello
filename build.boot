(set-env!
  :source-paths #{"src"}
  :dependencies '[[org.clojure/core.match          "0.3.0-alpha4"]
                  [org.clojure/clojure             "1.7.0"]
                  [org.clojure/clojurescript       "1.7.145"]
                  [adzerk/boot-test                "1.0.5"          :scope "test"]
                  [adzerk/boot-cljs                "1.7.170-2"      :scope "test"]
                  [crisptrutski/boot-cljs-test     "0.2.0-SNAPSHOT" :scope "test"]
                  [org.clojure/test.check          "0.8.2"          :scope "test"]
                  [adzerk/bootlaces                "0.1.13"         :scope "test"]])

(require '[adzerk.boot-cljs :refer [cljs]]
         '[crisptrutski.boot-cljs-test :refer [test-cljs]]
         '[adzerk.boot-test :refer [test]]
         '[adzerk.bootlaces :refer :all])

(def +version+ "0.2.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
  pom {:project 'othello
       :version +version+
       :description "An implementation of Operational Transform"
       :url "http://github.com/jahfer/othello"
       :license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}}
  test-cljs {:js-env :phantom})

(deftask testing
  "Profile setup for running tests."
  []
  (set-env! :source-paths #(conj % "test"))
  identity)
