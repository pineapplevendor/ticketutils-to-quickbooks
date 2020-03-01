(ns accounting-integrations-website.times)

(defn get-current-time-in-seconds []
  (/ (.getTime (java.util.Date.)) 1000))

(def ticket-utils-date-format "yyyy-MM-dd'T'HH:mm")

(defn format-ticket-utils-date [date]
  (.format (java.text.SimpleDateFormat. ticket-utils-date-format) date))

(defn parse-ticket-utils-date [formatted-date]
  (.parse (java.text.SimpleDateFormat. ticket-utils-date-format) formatted-date))

(defn format-view-date [date]
  (.format (java.text.SimpleDateFormat. "MM/dd/yyyy") date))

