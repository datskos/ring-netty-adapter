(defproject ring-netty-adapter "0.0.1"
  :repositories [["JBoss" "http://repository.jboss.org/nexus/content/groups/public/"]]
  :description "Ring Netty adapter"
  :dependencies [[org.clojure/clojure "1.2.0-master-SNAPSHOT"]
                 [org.clojure/clojure-contrib "1.2.0-SNAPSHOT"]
                 [org.jboss.netty/netty       "3.2.1.Final"]
                 [swank-clojure "1.2.1"]]
  :namespaces [ring.adapter.netty ring.adapter.plumbing])
