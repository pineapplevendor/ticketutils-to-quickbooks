(ns accounting-integrations-website.ticket-utils
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [buddy.core.mac :as mac]
            [clj-http.client :as client]
            [accounting-integrations-website.purchase-orders :as purchase-orders]
            [accounting-integrations-website.invoices :as invoices])
  (:import (java.util Base64)))

(def page-size 50)
(def invoices-path "/POS/v3.2/Sales/Invoices/Search")
(def purchase-orders-path "/POS/PurchaseOrders/Search")
(def customers-path "/POS/Customers/")

(def ticket-utils-domain "https://api.ticketutils.com")

(defn sign-request [secret path]
  (String. (.encode (Base64/getEncoder) (mac/hash path {:key secret :alg :hmac+sha256}))))

(defn get-ticket-utils-headers [secret token path]
  {"Content-Type" "application/json"
   "X-Token" token
   "X-Signature" (sign-request secret path)})

(defn get-ticket-utils [secret token path]
  (client/get (str ticket-utils-domain path) {:headers (get-ticket-utils-headers secret token path)}))

(defn post-ticket-utils [secret token path body]
  (client/post (str ticket-utils-domain path)
               {:body (json/write-str body)
                :headers (get-ticket-utils-headers secret token path)}))

(def ticket-utils-date-format "yyyy-MM-dd'T'HH:mm")

(defn format-date [date]
  (.format (java.text.SimpleDateFormat. ticket-utils-date-format) date))

(defn parse-date [formatted-date]
  (.parse (java.text.SimpleDateFormat. ticket-utils-date-format) formatted-date))

(defn get-response-body [http-response]
  (json/read-str (:body http-response) :key-fn keyword))

(defn get-invoices-request-body [start-date end-date page-size current-page]
  {:InvoiceDate {:FromDate (format-date start-date)
                 :ToDate (format-date end-date)
                 :FilterByTime true}
   :IncludeItems false
   :Page current-page
   :ItemsPerPage page-size})

(defn get-purchase-orders-request-body [start-date end-date page-size current-page]
  {:PurchaseOrderDate {:FromDate (format-date start-date)
                       :ToDate (format-date end-date)
                       :FilterByTime true}
   :Page current-page
   :ItemsPerPage page-size})

(defn get-invoice-from-item [{:keys [InvoiceId InvoiceNumber InvoiceDate ZoneCode ExternalOrderNumber PaymentStatus]
                                  {:keys [Amount]} :GrandTotal}]
  (invoices/map->Invoice {:customer ZoneCode 
                          :number InvoiceNumber 
                          :date (parse-date InvoiceDate) 
                          :amount Amount
                          :payment-complete? (= PaymentStatus 3)}))

(defn get-customer-name [secret token customer-id]
  (let [{:keys [] {:keys [FirstName LastName]} :BillingInfo} 
        (get-response-body (get-ticket-utils secret token (str customers-path customer-id)))]
    (str/trim (str FirstName " " LastName))))

(defn get-po-from-item [secret token 
                        {:keys [PurchaseOrderId PONumber PurchaseOrderDate Company CustomerId PaymentStatus]
                         {:keys [Amount]} :GrandTotal}]
  (purchase-orders/map->PurchaseOrder {:vendor (if (empty? Company)
                                                 (get-customer-name secret token CustomerId)
                                                 Company)
                                       :number PONumber
                                       :date (parse-date PurchaseOrderDate)
                                       :amount Amount
                                       :payment-complete? (= PaymentStatus 3)}))

(defn get-items [secret token path partial-request-body-getter current-page]
  (let [request-body (partial-request-body-getter current-page)
        response (get-response-body (post-ticket-utils secret token path request-body))]
    (if (= (:Page response) (:TotalPages response))
           (:Items response)
           (concat (:Items response) (get-items secret token path partial-request-body-getter (inc current-page))))))

(defn get-invoices [secret token start-date end-date]
  (let [partial-request-body-getter (partial get-invoices-request-body start-date end-date page-size)]
    (map get-invoice-from-item (get-items secret token invoices-path partial-request-body-getter 1))))

(defn get-purchase-orders [secret token start-date end-date]
  (let [partial-request-body-getter (partial get-purchase-orders-request-body start-date end-date page-size)]
    (map #(get-po-from-item secret token %) 
         (get-items secret token purchase-orders-path partial-request-body-getter 1))))

