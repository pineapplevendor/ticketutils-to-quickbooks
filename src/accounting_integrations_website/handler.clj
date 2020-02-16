(ns accounting-integrations-website.handler
  (:require [accounting-integrations-website.views :as views]
            [accounting-integrations-website.quickbooks :as quickbooks]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [redirect]]
            [ring.adapter.jetty :refer [run-jetty]]
            [environ.core :refer [env]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:gen-class))

(defroutes app-routes
  (GET "/" [] (views/home))
  (GET "/privacy" [] (views/privacy))
  (GET "/export-auth" [] (redirect (quickbooks/get-authorization-url)))
  (GET "/export-data" [realmId code] (views/export-data-page realmId code))
  (POST "/export-data" {form-params :params} (views/export-data-results-page form-params))
  (route/not-found "Not Found"))

(def app (wrap-defaults app-routes site-defaults))

(defn get-port []
  (or (Integer/parseInt (env :port)) 3000))

(defn -main [& args]
  (let [port (get-port)]
    (run-jetty app {:port port})))

