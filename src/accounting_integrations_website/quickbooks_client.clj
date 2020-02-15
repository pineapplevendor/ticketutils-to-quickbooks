(ns accounting-integrations-website.quickbooks-client
  (:require [environ.core :refer [env]])
  (:import (com.intuit.ipp.services DataService)
           (com.intuit.ipp.core ServiceType Context)
           (com.intuit.ipp.util Config)
           (com.intuit.ipp.security OAuth2Authorizer)))

(defn get-endpoint []
  (env :quickbooks-endpoint))

(defn get-data-service [access-token realm-id]
  (let [authorizer (OAuth2Authorizer. access-token)
        context (Context. authorizer (ServiceType/QBO) realm-id)]
    (Config/setProperty (Config/BASE_URL_QBO) (get-endpoint))
    (DataService. context)))

