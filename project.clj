(defproject ring-netty-adapter "0.0.3"
  :repositories [["JBoss" "http://repository.jboss.org/nexus/content/groups/public/"]]
  :description "Ring Netty adapter"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.jboss.netty/netty       "3.2.7.Final"]
                 [ring "1.1.0"]]
  :namespaces [ring.adapter.netty ring.adapter.plumbing])
