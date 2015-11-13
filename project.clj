(defproject othello "0.1.0-SNAPSHOT"
  :description "An implementation of Operational Transform"
  :url "http://github.com/jahfer/othello"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]]

  :jar-exclusions [#"\.swp|\.swo|\.DS_Store"]
  :prep-tasks ["javac" "compile"]
  :source-paths ["src"]
  :test-paths ["target/test-classes"]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.1.1-SNAPSHOT"]
                             [com.cemerick/clojurescript.test "0.3.3"]]
                   :dependencies [[org.clojure/clojurescript "1.7.145"]
                                  [org.clojure/test.check "0.8.2"]]
                   :aliases {"cleantest" ["do" "clean," "cljx" "once," "test," "cljsbuild" "test"]}}}

  :auto-clean false

  :cljsbuild {:builds {:test {:source-paths ["target/classes" "target/test-classes"]
                              :compiler {:output-to "target/cljs/main.js"
                                         :optimizations :advanced
                                         :pretty-print true}}}
              :test-commands {"unit" ["slimerjs" :runner "target/cljs/main.js"]}})
