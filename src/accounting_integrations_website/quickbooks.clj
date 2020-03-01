(ns accounting-integrations-website.quickbooks
  (:require [environ.core :refer [env]]
            [accounting-integrations-website.quickbooks-client :as quickbooks-client]
            [clojure.string :as str]
            [clj-http.client :as client])
  (:import (java.util ArrayList)
           (com.intuit.ipp.data Item Account ItemTypeEnum ReferenceType Vendor Customer ReferenceType Line
                                LineDetailTypeEnum SalesItemLineDetail Invoice PurchaseOrder
                                ItemBasedExpenseLineDetail BillableStatusEnum PurchaseOrderStatusEnum)
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

(defn get-login-redirect-url []
  (env :quickbooks-logged-in-url))

(defn get-connect-redirect-url []
  (env :quickbooks-connected-url))

(defn get-oauth-config []
  (.buildConfig 
    (.callDiscoveryAPI (OAuth2Config$OAuth2ConfigBuilder. (get-client-id) (get-client-secret)) 
                       (get-env))))

(defn get-login-url []
  (let [oauth-config (get-oauth-config)
        csrf (.generateCSRFToken oauth-config)
        scopes (doto (ArrayList.) (.add Scope/OpenIdAll))]
    (.prepareUrl oauth-config scopes (get-login-redirect-url) csrf)))

(defn get-open-id-tokens [auth-code]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)
        bearer-token-response (.retrieveBearerTokens client auth-code (get-login-redirect-url))]
    {:id-token (.getIdToken bearer-token-response)
     :refresh-token (.getRefreshToken bearer-token-response)
     :access-token (.getAccessToken bearer-token-response)}))

(defn get-authorization-url []
  (let [oauth-config (get-oauth-config)
        csrf (.generateCSRFToken oauth-config)
        scopes (doto (ArrayList.) (.add Scope/Accounting))]
    (.prepareUrl oauth-config scopes (get-connect-redirect-url) csrf)))

(defn get-tokens-from-bearer-token [bearer-token-response]
  {:access-token (.getAccessToken bearer-token-response)
   :access-token-expiration (.getExpiresIn bearer-token-response)
   :refresh-token (.getRefreshToken bearer-token-response)
   :refresh-token-expiration (.getXRefreshTokenExpiresIn bearer-token-response)})

(defn get-tokens [auth-code]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)
        bearer-token-response (.retrieveBearerTokens client auth-code (get-connect-redirect-url))]
    (get-tokens-from-bearer-token bearer-token-response)))

(defn refresh-tokens [refresh-token]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)
        bearer-token-response (.refreshToken client refresh-token)]
    (get-tokens-from-bearer-token bearer-token-response)))

(defn get-access-token [auth-code]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)
        bearer-token-response (.retrieveBearerTokens client auth-code (get-connect-redirect-url))]
    (.getAccessToken bearer-token-response)))

(defn validate-id-token [id-token]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)]
    (.validateIDToken client id-token)))

(defn get-user-id [access-token]
  (let [oauth-config (get-oauth-config)
        client (OAuth2PlatformClient. oauth-config)]
    (.getSub (.getUserInfo client access-token))))

(defn create-entity [data-service entity]
  (.add data-service entity))

(defn update-entity [data-service entity]
  (.update data-service entity))

(defn delete-entity [data-service entity]
  (.delete data-service entity))

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

(defn get-invoice [data-service doc-number]
  (let [query (str "select * from Invoice where DocNumber = '" doc-number "'")]
    (get-entity data-service query)))

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

(defn sync-invoice [data-service invoice]
  (let [current-invoice (get-invoice data-service (:number invoice))]
    (if (not current-invoice)
      {:created (create-invoice data-service invoice)}
      {:existing current-invoice})))

(defn sync-invoice-with-service [access-token realm-id invoice]
  (let [data-service (quickbooks-client/get-data-service access-token realm-id)]
    (sync-invoice data-service invoice)))

(defn sync-invoices [access-token realm-id invoices]
  (pmap #(sync-invoice-with-service access-token realm-id %) invoices))

(defn get-purchase-order [data-service doc-number]
  (let [query (str "select * from PurchaseOrder where DocNumber = '" doc-number "'")]
    (get-entity data-service query)))

(defn mark-po-as-paid [data-service purchase-order]
  (doto purchase-order
    (.setSparse true)
    (.setPOStatus PurchaseOrderStatusEnum/CLOSED))
  (update-entity data-service purchase-order))

;;requires update if PO has been marked as payment complete since it was created in QuickBooks
;;does NOT update for items marked as completed in QuickBooks, but not completed in TicketUtils
(defn purchase-order-requires-update? [quickbooks-po ticket-utils-po]
  (and (:payment-complete? ticket-utils-po)
       (= PurchaseOrderStatusEnum/OPEN (.getPOStatus quickbooks-po))))

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

(defn sync-purchase-order [data-service purchase-order]
  (let [current-purchase-order (get-purchase-order data-service (:number purchase-order))]
    (cond
      (not current-purchase-order) 
      {:created (create-purchase-order data-service purchase-order)}
      (purchase-order-requires-update? current-purchase-order purchase-order) 
      {:updated (mark-po-as-paid data-service purchase-order)}
      :else
      {:existing current-purchase-order})))

(defn sync-purchase-order-with-service [access-token realm-id purchase-order]
  (let [data-service (quickbooks-client/get-data-service access-token realm-id)]
    (sync-purchase-order data-service purchase-order)))

(defn sync-purchase-orders [access-token realm-id purchase-orders]
  (pmap #(sync-purchase-order-with-service access-token realm-id %) purchase-orders))

