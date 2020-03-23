(ns accounting-integrations-website.auth-handler
  (:require [environ.core :refer [env]]
            [accounting-integrations-website.times :as times]
            [accounting-integrations-website.session-controller :as session-controller]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [taoensso.faraday :as far]))

(comment
(defn get-user-state [session]
  (if-let [access-token (:access-token session)]
    (if-let [user-id (quickbooks/get-user-id access-token)]
      (refresh-tokens-if-necessary (assoc (get-tokens user-id) :user-id user-id)))))
)

(defn get-user-state [session]
  (if-let [user-id (and (:access-token session) (quickbooks/get-user-id (:access-token session)))]
    (merge
      {:is-logged-in? (boolean user-id) :user-id user-id}
      (select-keys (session-controller/get-refreshed-tokens! user-id) [:access-token :realm-id]))
    {:is-logged-in? false}))

(defn wrap-user-state [handler]
  (fn [request]
    (handler (assoc request :user-state (get-user-state (:session request))))))

