(ns accounting-integrations-website.export-controller
  (:require [accounting-integrations-website.quickbooks :as quickbooks]
            [accounting-integrations-website.ticket-utils :as ticket-utils]))

(defn summarize-export [synced]
  {:existing (count (filter some? (map :existing synced)))
   :created (count (filter some? (map :created synced)))
   :updated (count (filter some? (map :updated synced)))})

(defn get-accounts-payable-accounts [access-token realm-id]
  (quickbooks/get-accounts-payable-accounts access-token realm-id))

(defn sync-data 
  [item accounts-payable-account start-date end-date ticket-utils-token ticket-utils-secret realm-id access-token]
  (summarize-export 
    (if (= :invoices item)
      (quickbooks/sync-invoices
        access-token
        realm-id
        (ticket-utils/get-invoices ticket-utils-secret ticket-utils-token start-date end-date))
      (quickbooks/sync-purchase-orders
        access-token
        realm-id
        accounts-payable-account
        (ticket-utils/get-purchase-orders ticket-utils-secret ticket-utils-token start-date end-date)))))

