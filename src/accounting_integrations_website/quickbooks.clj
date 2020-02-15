(ns accounting-integrations-website.quickbooks
  (:require [environ.core :refer [env]]
            [accounting-integrations-website.quickbooks-client :as quickbooks-client]
            [clojure.string :as str]
            [clj-http.client :as client])
  (:import (java.util ArrayList)
           (com.intuit.ipp.data Item Account ItemTypeEnum ReferenceType Vendor Customer ReferenceType Line
                                LineDetailTypeEnum SalesItemLineDetail Invoice PurchaseOrder
                                ItemBasedExpenseLineDetail BillableStatusEnum)
           (com.intuit.ipp.services DataService)
           (com.intuit.ipp.core ServiceType Context)
           (com.intuit.ipp.util Config)
           (com.intuit.ipp.security OAuth2Authorizer)
           (com.intuit.oauth2.client OAuth2PlatformClient)
           (com.intuit.oauth2.config Scope OAuth2Config OAuth2Config$OAuth2ConfigBuilder Environment)))

(def tickets-item "TicketUtilsTickets")

(defn get-env []
  (Environment/fromValue (env :quickbooks-env)))

(defn get-client-id []
  (env :quickbooks-client-id))

(defn get-client-secret []
  (env :quickbooks-client-secret))

(defn get-redirect-url []
  (env :quickbooks-redirect-url))

(defn get-oauth-config []
  (.buildConfig 
    (.callDiscoveryAPI (OAuth2Config$OAuth2ConfigBuilder. (get-client-id) (get-client-secret)) 
                       (get-env))))

(defn get-authorization-url []
  (let [oauth-config (get-oauth-config)
        csrf (.generateCSRFToken oauth-config)
        scopes (doto (ArrayList.) (.add Scope/Accounting))]
    (.prepareUrl oauth-config scopes (get-redirect-url) csrf)))

(defn get-access-token [auth-code]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)
        bearer-token-response (.retrieveBearerTokens client auth-code (get-redirect-url))]
    (.getAccessToken bearer-token-response)))

(def ticket-utils-date-format "yyyy-MM-dd")

(defn format-date [date]
  (.format (java.text.SimpleDateFormat. ticket-utils-date-format) date))

(defn create-entity [data-service entity]
  (.add data-service entity))

(defn create-entities [data-service entities]
  (map #(.add data-service %) entities))

(defn get-entities [data-service query]
  (.getEntities (.executeQuery data-service query)))

(defn get-entity [data-service query]
  (first (get-entities data-service query)))

(defn get-entity-id [data-service query]
  (if-let [entity (get-entity data-service query)]
    (.getId entity)
    nil))

(defn get-tickets-item-id [data-service]
  (let [query (str "select * from Item where Name='" tickets-item "'")]
    (get-entity-id data-service query)))

(defn create-tickets-item [data-service]
  (let [item (doto (Item.)
               (.setTrackQtyOnHand false)
               (.setName tickets-item)
               (.setIncomeAccountRef (doto (ReferenceType.) (.setValue "81"))) ;;Inventory Asset Account
               (.setExpenseAccountRef (doto (ReferenceType.) (.setValue "78"))) ;;Purchases Account
               (.setType (ItemTypeEnum/SERVICE)))]
    (create-entity data-service item)))

(defn get-vendor-name [vendor-name]
  (str vendor-name "-Vendor"))

(defn get-vendor-id [data-service vendor-name]
  (let [query (str "select * from vendor where DisplayName='" (get-vendor-name vendor-name) "'")]
    (get-entity-id data-service query)))

(defn create-vendor [data-service vendor-name]
  (let [vendor (doto (Vendor.) (.setDisplayName (get-vendor-name vendor-name)))]
    (create-entity data-service vendor)))

(defn get-customer-name [customer-name]
  (str customer-name "-Customer"))

(defn get-customer-id [data-service customer-name]
  (let [query (str "select * from Customer where DisplayName='" (get-customer-name customer-name) "'")]
    (get-entity-id data-service query)))

(defn create-customer [data-service customer-name]
  (let [customer (doto (Customer.) (.setDisplayName (get-customer-name customer-name)))]
    (create-entity data-service customer)))

(defn get-invoice-line [customer-id tickets-item-id invoice]
  (doto (Line.)
    (.setAmount (BigDecimal. (:amount invoice)))
    (.setDetailType (LineDetailTypeEnum/SALES_ITEM_LINE_DETAIL))
    (.setDescription (str "TicketUtils Invoice: " (:number invoice)))
    (.setSalesItemLineDetail (doto (SalesItemLineDetail.)
                               (.setItemRef (doto (ReferenceType.) (.setValue tickets-item-id)))))))

(defn create-invoice [data-service invoice]
  (let [customer-id (or (get-customer-id data-service (:customer invoice))
                        (create-customer data-service (:customer invoice)))
        tickets-item-id (get-tickets-item-id data-service)]
    (let [invoice (doto (Invoice.)
                    (.setDocNumber (str (:number invoice)))
                    (.setTxnDate (:date invoice))
                    (.setCustomerRef (doto (ReferenceType.) (.setValue customer-id)))
                    (.setLine (doto (ArrayList.) (.add (get-invoice-line customer-id tickets-item-id invoice)))))]
      (create-entity data-service invoice))))

(defn create-invoice-with-service [access-token realm-id invoice]
  (let [data-service (quickbooks-client/get-data-service access-token realm-id)]
    (create-invoice data-service invoice)))

(defn create-invoices [access-token realm-id invoices]
  (pmap #(create-invoice-with-service access-token realm-id %) invoices))

(defn get-purchase-order-line [vendor-id tickets-item-id purchase-order]
  (doto (Line.)
    (.setDescription (str "TicketUtils Purchase Order: " (:number purchase-order)))
    (.setDetailType (LineDetailTypeEnum/ITEM_BASED_EXPENSE_LINE_DETAIL))
    (.setAmount (BigDecimal. (:amount purchase-order)))
    (.setItemBasedExpenseLineDetail (doto (ItemBasedExpenseLineDetail.)
                                      (.setItemRef (doto (ReferenceType.) (.setValue tickets-item-id)))
                                      (.setTaxCodeRef (doto (ReferenceType.) (.setValue "NON")))
                                      (.setBillableStatus (BillableStatusEnum/NOT_BILLABLE))))))

(defn create-purchase-order [data-service purchase-order]
  (let [vendor-id (or (get-vendor-id data-service (:vendor purchase-order))
                      (create-vendor data-service (:vendor purchase-order)))
        tickets-item-id (get-tickets-item-id data-service)]
    (let [purchase-order (doto (PurchaseOrder.)
                           (.setDocNumber (str (:number purchase-order)))
                           (.setTxnDate (:date purchase-order))
                           (.setTotalAmt (BigDecimal. (:amount purchase-order)))
                           (.setVendorRef (doto (ReferenceType.) (.setValue vendor-id)))
                           (.setAPAccountRef (doto (ReferenceType.) (.setValue "33"))) ;;Accounts Payable (A/P)
                           (.setLine (doto (ArrayList.) 
                                       (.add (get-purchase-order-line vendor-id tickets-item-id purchase-order)))))]
      (create-entity data-service purchase-order))))

(defn create-purchase-order-with-service [access-token realm-id purchase-order]
  (let [data-service (quickbooks-client/get-data-service access-token realm-id)]
    (create-purchase-order data-service purchase-order)))

(defn create-purchase-orders [access-token realm-id purchase-orders]
  (pmap #(create-purchase-order-with-service access-token realm-id %) purchase-orders))


