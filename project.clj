(defproject othello "0.1.0-SNAPSHOT"
  :description "An implementation of Operational Transform"
  :url "http://github.com/jahfer/othello"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :plugins [[com.cemerick/clojurescript.test "0.3.3"]
            [com.keminglabs/cljx "0.6.0"]
            [lein-cljsbuild "1.0.5"]]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3126"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/test.check "0.7.0"]]

  :prep-tasks [["cljx" "once"] "javac" "compile"]
  :source-paths ["target/generated/src/clj"]
  :test-paths ["target/generated/test/clj"]

  :cljx {:builds [{:source-paths ["src"]
                   :output-path "target/generated/src/clj"
                   :rules :clj}
                  {:source-paths ["src"]
                   :output-path "target/generated/src/cljs"
                   :rules :cljs}
                  {:source-paths ["test"]
                   :output-path "target/generated/test/clj"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/generated/test/cljs"
                   :rules :cljs}]}

   :cljsbuild {:builds {:test {:source-paths ["target/generated/src/cljs" "target/generated/test/cljs"]
                              :compiler {:output-to "target/cljs/main.js"
                                         :optimizations :simple
                                         :source-map "target/cljs/main.js.map"}}}

               :test-commands {"unit" ["slimerjs" :runner "target/cljs/main.js"]}}

   :aliases {"cleantest" ["do" "clean," "cljx" "once," "test," "cljsbuild" "test"]})
