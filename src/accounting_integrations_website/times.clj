(ns accounting-integrations-website.times)

(defn get-current-time-in-seconds []
  (/ (.getTime (java.util.Date.)) 1000))


