(ns accounting-integrations-website.purchase-orders
  (:require [schema.core :as s]))

(s/defrecord PurchaseOrder
  [vendor :- s/Str
   number :- s/Int
   date :- java.util.Date
   amount :- s/Num])

