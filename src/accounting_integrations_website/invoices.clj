(ns accounting-integrations-website.invoices
  (:require [schema.core :as s]))

(s/defrecord Invoice
  [customer :- s/Str
   number :- s/Int
   date :- java.util.Date
   amount :- s/Num])

