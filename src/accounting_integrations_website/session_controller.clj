(ns accounting-integrations-website.session-controller
  (:require [environ.core :refer [env]]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [taoensso.faraday :as far]))

(defn get-users-table []
  (env :users-table))

(defn get-dynamo-options []
  {:access-key (env :aws-access-key-id)
   :secret-key (env :aws-secret-access-key)
   :endpoint (env :dynamo-endpoint)})

(defn get-current-time-in-seconds []
  (/ (.getTime (java.util.Date.)) 1000))

(defn get-expiration-date-in-seconds [seconds-to-expiration]
  ;;subtract 60 seconds for a minute of padding
  (let [current-time (get-current-time-in-seconds)
        padding 60]
    (- (+ current-time seconds-to-expiration) padding)))

(defn set-tokens [user-id auth-code realm-id]
  (far/put-item (get-dynamo-options)
                (get-users-table)
                (merge (quickbooks/get-tokens auth-code) 
                       {:user-id user-id :realm-id realm-id})))

(defn get-validated-token [item token-key expiration-key]
  (cond (and (token-key item)
             (< (get-current-time-in-seconds) (expiration-key item)))
        (token-key item)))

(defn get-valid-tokens [item]
  (assoc item
         :access-token (get-validated-token item :access-token :access-token-expiration)
         :refresh-token (get-validated-token item :refresh-token :refresh-token-expiration)))

(defn get-tokens [user-id]
  (cond user-id
    (select-keys (get-valid-tokens (far/get-item (get-dynamo-options) (get-users-table) {:user-id user-id}))
                 [:access-token :refresh-token :realm-id])))

(defn get-user-state [session]
  (merge (select-keys session [:user-id])
         (get-tokens (:user-id session))))


