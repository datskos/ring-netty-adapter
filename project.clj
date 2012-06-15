(defproject ring-netty-adapter/ring-netty-adapter "0.0.3"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [io.netty/netty "3.5.0.Final"]
                 [ring "1.1.0"]
                 [clj-http "0.4.2"]
                 [cheshire "4.0.0"]]
  :source-paths ["src/clojure"]
  :test-paths ["test/clojure"]
  :profiles {:dev {:dependencies [[expectations "1.4.3"]
                                  [junit/junit "4.8.1"]]}}
  :min-lein-version "2.0.0"
  :description "Ring Netty adapter")