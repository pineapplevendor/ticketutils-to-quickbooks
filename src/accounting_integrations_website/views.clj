(ns accounting-integrations-website.views
  (:require [hiccup.page :as page]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [accounting-integrations-website.export-controller :as export-controller]
            [accounting-integrations-website.input-form :as input-form]
            [ring.util.anti-forgery :as anti-forgery]))

(defn render-export-data-form [item start-date end-date ticket-utils-token ticket-utils-secret realm-id access-token]
  [:div {:id "export-data-form"}
   [:h1 "Export Accounting Data to QuickBooks"]
   [:form {:action "/export-data" :method "POST"}
    (anti-forgery/anti-forgery-field)
    [:p 
     [:h3 "What data would you like to export?"]
     [:div
      [:label {:for "invoices"} "Invoices " ]
      [:input {:type "radio" :id "invoices" :name "item" :value "invoices" :checked (= item "invoices")}]]
     [:div
      [:label {:for "purchase-orders"} "Purchase Orders "]
      [:input {:type "radio" :id "purchase-orders" :name "item" :value "purchase-orders" 
               :checked (= item "purchase-orders")}]]]
    [:p 
     [:h3 "What date range would you like to export the data from?"]
     [:div
      [:label {:for "start-date"} "Start date "]
      [:input {:type "date" :id "start-date" :name "start-date" :value (if start-date start-date "")}]]
     [:div 
      [:label {:for "end-date"} "End date "]
      [:input {:type "date" :id "end-date" :name "end-date" :value (if end-date end-date "")}]]]
    [:p 
     [:h3 "TicketUtils Information"]
     [:p "You can retrieve this information from "
      [:a {:href "https://app.ticketutils.com/Account/Dashboard" :target "_blank"} "here"]
      " under \"API Settings\""]
     [:div
      [:label {:for "ticket-utils-token"} "TicketUtils API Token "]
      [:input {:type "text", :id "ticket-utils-token", :name "ticket-utils-token", :size "15"
               :value (if ticket-utils-token ticket-utils-token "")}]]
     [:div 
      [:label {:for "ticket-utils-secret"} "TicketUtils API Secret "]
      [:input {:type "text", :id "ticket-utils-secret", :name "ticket-utils-secret", :size "15"
               :value (if ticket-utils-secret ticket-utils-secret "")}]]]
    [:input {:type "hidden" :id "realm-id" :name "realm-id" :value realm-id}]
    [:input {:type "hidden" :id "access-token" :name "access-token" :value access-token}]
    [:input {:type "submit" :value "Export Data"}]]])

(defn format-item [item]
  (if (= :invoices item)
    "invoices"
    "purchase orders"))

(defn format-date [date]
  (.format (java.text.SimpleDateFormat. "MM/dd/yyyy") date))

(defn export-data-results-page [unvalidated-form]
  (let [validated (input-form/get-validated-form unvalidated-form)]
    (if (:error-message validated)
      (page/html5
        [:h3 "Error: " (:error-message validated)]
        (render-export-data-form
          (:item unvalidated-form)
          (:start-date unvalidated-form)
          (:end-date unvalidated-form)
          (:ticket-utils-token unvalidated-form)
          (:ticket-utils-secret unvalidated-form)
          (:realm-id unvalidated-form)
          (:access-token unvalidated-form)))
      (do
        (def synced (export-controller/sync-data 
          (:item validated)
          (:start-date validated)
          (:end-date validated)
          (:ticket-utils-token validated)
          (:ticket-utils-secret validated)
          (:realm-id validated)
          (:access-token validated)))
        (page/html5
          [:h1 "Exported your data from " (format-date (:start-date validated))
           " to " (format-date (:end-date validated))]
          [:h3 "Created " (:created synced) " records"]
          [:h3 "Updated " (:updated synced) " records"]
          [:h3 (:existing synced) " records already existed"]
          [:p "Click "
           [:a {:href "/"} "here"]
           " to return home and export more data"])))))

(defn export-data-page [realm-id code]
  (page/html5
    (render-export-data-form nil nil nil nil nil realm-id (quickbooks/get-access-token code))))

(defn home []
  (page/html5
    [:h1 "Welcome to Can Opener Integrations"]
    [:h3 "This is a tool to export purchase orders and invoices from TicketUtils to QuickBooks"]
    [:p "Click " 
     [:a {:href "/export-auth"} "here"]
     " to authenticate with QuickBooks and export your data"]))

(defn privacy []
  (page/html5
    [:h1 "License"]
    [:p
     "This is free and unencumbered software released into the public domain.

      Anyone is free to copy, modify, publish, use, compile, sell, or
      distribute this software, either in source code form or as a compiled
      binary, for any purpose, commercial or non-commercial, and by any
      means.

      In jurisdictions that recognize copyright laws, the author or authors
      of this software dedicate any and all copyright interest in the
      software to the public domain. We make this dedication for the benefit
      of the public at large and to the detriment of our heirs and
      successors. We intend this dedication to be an overt act of
      relinquishment in perpetuity of all present and future rights to this
      software under copyright law.

      THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND,
      EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
      MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
      IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
      OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
      ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
      OTHER DEALINGS IN THE SOFTWARE.

      For more information, please refer to https://unlicense.org"]
    [:h1 "Privacy"]
    [:p 
     "The Can Opener Integration desktop app retrieves retrieves your purchase orders and invoices
      from TicketUtils. It uses that data to create purchase orders and invoices in QuickBooks."]))
