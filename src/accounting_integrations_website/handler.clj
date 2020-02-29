(ns accounting-integrations-website.handler
  (:require [accounting-integrations-website.views :as views]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [accounting-integrations-website.session-controller :as sessions]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [redirect]]
            [ring.adapter.jetty :refer [run-jetty]]
            [environ.core :refer [env]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:gen-class))

(defroutes app-routes
  (GET "/" request (views/home (:session request)))
  (GET "/login" [] (redirect (quickbooks/get-login-url)))
  (GET "/logged-in" [code :as r] (sessions/get-logged-in-response (:session r) code))
  (GET "/connect-to-quickbooks" [] (redirect (quickbooks/get-authorization-url)))
  (GET "/connected-to-quickbooks" [realmId code :as r] (sessions/get-connected-response (:session r) code realmId))
  (GET "/privacy" [] (views/privacy))
  (GET "/export-auth" [] (redirect (quickbooks/get-authorization-url)))
  (GET "/export-data" [realmId code] (views/export-data-page realmId code))
  (POST "/export-data" {form-params :params} (views/export-data-results-page form-params))
  (route/not-found "Not Found"))

;;set this to lax so the session is still there when redirected back from quickbooks oauth
(def config (assoc-in site-defaults [:session :cookie-attrs] {:same-site :lax}))

(def app (wrap-defaults app-routes config))

(defn get-port []
  (or (Integer/parseInt (env :port)) 3000))

(defn -main [& args]
  (let [port (get-port)]
    (run-jetty app {:port port})))

