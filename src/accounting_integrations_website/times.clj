(ns accounting-integrations-website.times)

(def ticket-utils-date-format "yyyy-MM-dd'T'HH:mm")
(def form-date-format "yyyy-MM-dd")
(def view-date-format "MM/dd/yyyy")

(defn get-current-time-in-seconds []
  (/ (.getTime (java.util.Date.)) 1000))

(defn format-ticket-utils-date [date]
  (.format (java.text.SimpleDateFormat. ticket-utils-date-format) date))

(defn parse-ticket-utils-date [formatted-date]
  (.parse (java.text.SimpleDateFormat. ticket-utils-date-format) formatted-date))

(defn format-view-date [date]
  (.format (java.text.SimpleDateFormat. view-date-format) date))

(defn parse-form-date [date]
  (.parse (java.text.SimpleDateFormat. form-date-format) date))

