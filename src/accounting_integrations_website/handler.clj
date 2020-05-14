(ns accounting-integrations-website.handler
  (:require [accounting-integrations-website.views :as views]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [accounting-integrations-website.session-controller :as sessions]
            [accounting-integrations-website.auth-handler :as auth-handler]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [redirect]]
            [ring.adapter.jetty :refer [run-jetty]]
            [environ.core :refer [env]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session-timeout :refer [wrap-absolute-session-timeout]]
            [ring.logger :as logger]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:gen-class))

(defroutes app-routes
  (GET "/" request (views/home request))
  (GET "/login" [] (redirect (quickbooks/get-login-url)))
  (GET "/logged-in" [code :as request] (sessions/get-logged-in-response (:session request) code))
  (GET "/logged-out" request (sessions/get-logged-out-response request))
  (GET "/connect-to-quickbooks" [] (redirect (quickbooks/get-authorization-url)))
  (GET "/connected-to-quickbooks" [realmId code :as r] (sessions/get-connected-response (:session r) code realmId))
  (POST "/disconnect" request (views/disconnected-results-page request))
  (GET "/export-data" request (views/export-data-page request))
  (POST "/export-data" request (views/export-data-results-page request (:params request)))
  (GET "/privacy" [] (views/privacy))
  (route/not-found "Not Found"))

;;set this to lax so the session is still there when redirected back from quickbooks oauth
(def config (assoc-in site-defaults [:session :cookie-attrs] {:same-site :lax}))

(defn wrap-exception-handling [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (println "A request failed with the following exception." e)
        {:status 400 :body "Apologies, we've encountered an error. Please contact echavis@umich.edu for support."}))))

(def app 
  (-> app-routes
      (logger/wrap-with-logger {:log-fn (fn [{:keys [level throwable message]}]
                                          (println "level: " level)
                                          (cond throwable (println "throwable: " throwable))
                                          (println "message: " message))
                                :redact-key? #{:code :ticket-utils-token :ticket-utils-secret :access-token}})
      (wrap-absolute-session-timeout {:timeout 1740 ;29 minutes * 60 seconds/minute = 1740 seconds
                                      :timeout-handler sessions/get-logged-out-response})
      (auth-handler/wrap-user-state)
      (wrap-defaults config)
      (wrap-exception-handling)))

(defn get-port []
  (or (Integer/parseInt (env :port)) 3000))

(defn -main [& args]
  (let [port (get-port)]
    (run-jetty app {:port port})))

