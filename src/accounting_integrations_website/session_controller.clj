(ns accounting-integrations-website.session-controller
  (:require [environ.core :refer [env]]
            [accounting-integrations-website.times :as times]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [taoensso.faraday :as far]))

(defn get-users-table []
  (env :users-table))

(defn get-dynamo-options []
  {:access-key (env :aws-access-key-id)
   :secret-key (env :aws-secret-access-key)
   :endpoint (env :dynamo-endpoint)})

(defn get-expiration-date-in-seconds [seconds-to-expiration]
  ;;subtract 60 seconds for a minute of padding
  (let [current-time (times/get-current-time-in-seconds)
        padding 60]
    (int (Math/floor (- (+ current-time seconds-to-expiration) 
                        padding)))))

(defn get-tokens-with-expirations [tokens]
  (-> tokens
      (update-in [:access-token-expiration] get-expiration-date-in-seconds)
      (update-in [:refresh-token-expiration] get-expiration-date-in-seconds)))

(defn set-tokens [access-token auth-code realm-id]
  (far/put-item (get-dynamo-options)
                (get-users-table)
                (merge (get-tokens-with-expirations (quickbooks/get-tokens auth-code))
                       {:user-id (quickbooks/get-user-id access-token)
                        :realm-id realm-id})))

(defn get-connected-response [session auth-code realm-id]
  (set-tokens (:access-token session) auth-code realm-id)
  {:status 302
   :headers {"Location" "/"}})

(defn get-tokens-for-session [auth-code]
  (let [open-id-tokens (quickbooks/get-open-id-tokens auth-code)]
    (cond (quickbooks/validate-id-token (:id-token open-id-tokens))
          open-id-tokens)))

(defn get-logged-in-response [session auth-code]
  {:status 302
   :headers {"Location" "/"}
   :session (merge session (get-tokens-for-session auth-code))})

(defn get-validated-token [item token-key expiration-key]
  (cond (and (token-key item)
             (< (times/get-current-time-in-seconds) (expiration-key item)))
        (token-key item)))

(defn get-valid-tokens [dynamo-item]
  (assoc dynamo-item
         :access-token (get-validated-token dynamo-item :access-token :access-token-expiration)
         :refresh-token (get-validated-token dynamo-item :refresh-token :refresh-token-expiration)))

(defn get-tokens [user-id]
  (cond user-id
    (select-keys (get-valid-tokens (far/get-item (get-dynamo-options) (get-users-table) {:user-id user-id}))
                 [:access-token :refresh-token :realm-id])))

(defn get-refreshed-tokens! [user-id]
  (let [current-tokens (assoc (get-tokens user-id) :user-id user-id)]
    (if (and (:refresh-token current-tokens) (not (:access-token current-tokens)))
      (let [refreshed-tokens (quickbooks/refresh-tokens (:refresh-token current-tokens))]
        (far/put-item (get-dynamo-options)
                      (get-users-table)
                      (merge (get-tokens-with-expirations refreshed-tokens)
                             {:user-id (:user-id current-tokens) 
                              :realm-id (:realm-id current-tokens)})))
      current-tokens)))

(defn get-user-state [request]
  (:user-state request))

(defn is-logged-in? [user-state]
  (:user-id user-state))

(defn is-connected-to-quickbooks? [user-state]
  (and (:access-token user-state) (:realm-id user-state)))

(defn disconnect [request]
  (let [user-state (get-user-state request)]
    (far/delete-item (get-dynamo-options) (get-users-table) (select-keys user-state [:user-id]))
    (quickbooks/disconnect (:refresh-token user-state))))

