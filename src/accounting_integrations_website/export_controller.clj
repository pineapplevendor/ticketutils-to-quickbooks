(ns accounting-integrations-website.export-controller
  (:require [accounting-integrations-website.quickbooks :as quickbooks]
            [accounting-integrations-website.ticket-utils :as ticket-utils]))

(defn export-data [item start-date end-date ticket-utils-token ticket-utils-secret realm-id access-token]
  (if (= :invoices item)
    (quickbooks/sync-invoices
      access-token
      realm-id
      (ticket-utils/get-invoices ticket-utils-secret ticket-utils-token start-date end-date))
    (quickbooks/sync-purchase-orders
      access-token
      realm-id
      (ticket-utils/get-purchase-orders ticket-utils-secret ticket-utils-token start-date end-date))))

