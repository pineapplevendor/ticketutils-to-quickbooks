(ns accounting-integrations-website.input-form
  (:require [schema.core :as s]))

(def form-date-format "yyyy-MM-dd")

(defn validate-item [item]
  (if (contains? (set ["invoices" "purchase-orders"]) item)
    {:item (keyword item)}
    {:error-message "Either Invoices or Purchase Orders must be selected."}))

(defn validate-date [date-name date]
  (try
       {(keyword date-name) (.parse (java.text.SimpleDateFormat. form-date-format) date)}
       (catch Exception e
         {:error-message "Dates must be formatted as mm/dd/yyyy."})))

(defn validate-non-empty-string [str-name input error-message]
  (if (empty? input)
    {:error-message error-message}
    {(keyword str-name) input}))

(defn validate-ticket-utils-token [token]
  (validate-non-empty-string 
    "ticket-utils-token"
    token 
    "You must provide your TicketUtils API Token to export data. Please follow the form's instructions to retrieve it."))

(defn validate-ticket-utils-secret [secret]
  (validate-non-empty-string
    "ticket-utils-secret"
    secret
    "You must provide your TicketUtils API Secret to export data. Please follow the form's instructions to retrieve it."))

(defn validate-realm-id [realm-id]
  (validate-non-empty-string
    "realm-id"
    realm-id
    "We were unable to retrieve a realm ID based on your quickbooks login. Please try again later."))

(defn validate-access-token [access-token]
  (validate-non-empty-string
    "access-token"
    access-token 
    "We were unable to retrieve an access token based on your quickbooks login. Please try again later."))

(defn get-validated-form 
  [{:keys [item start-date end-date ticket-utils-token ticket-utils-secret realm-id access-token]}]
  (merge
    (validate-item item)
    (validate-date "start-date" start-date)
    (validate-date "end-date" end-date)
    (validate-ticket-utils-secret ticket-utils-secret)
    (validate-ticket-utils-token ticket-utils-token)
    (validate-realm-id realm-id)
    (validate-access-token access-token)))

