(defproject othello "0.1.0-SNAPSHOT"
  :description "An implementation of Operational Transform"
  :url "http://github.com/jahfer/othello"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]]

  :jar-exclusions [#"\.cljx|\.swp|\.swo|\.DS_Store"]
  :prep-tasks [["cljx" "once"] "javac" "compile"]
  :source-paths ["src"]
  :test-paths ["target/test-classes"]

  :profiles {:dev {:plugins [[com.keminglabs/cljx "0.6.0"]
                             [lein-cljsbuild "1.0.5"]
                             [com.cemerick/clojurescript.test "0.3.3"]]
                   :dependencies [[org.clojure/clojurescript "0.0-3126"]
                                  [org.clojure/test.check "0.7.0"]]
                   :aliases {"cleantest" ["do" "clean," "cljx" "once," "test," "cljsbuild" "test"]}}}

  :auto-clean false

  :cljx {:builds [{:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :cljs}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :cljs}]}

   :cljsbuild {:builds {:test {:source-paths ["target/classes" "target/test-classes"]
                              :compiler {:output-to "target/cljs/main.js"
                                         :optimizations :advanced
                                         :pretty-print true}}}

               :test-commands {"unit" ["slimerjs" :runner "target/cljs/main.js"]}})
