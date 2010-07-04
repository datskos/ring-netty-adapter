; A very simple Ring application.

(ns ring.example.hello-world
  (:use ring.adapter.netty)
  (:import java.util.Date java.text.SimpleDateFormat))

(defn app
  [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (str "<h3>Hello World from Ring and Netty</h3>"
                 "<p>The current time is "
                 (.format (SimpleDateFormat. "HH:mm:ss") (Date.))
                 ".</p>")})

(run-netty app {:port 8080})
