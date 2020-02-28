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
  (GET "/" request (println request) (views/home (:session request)))
  (GET "/login" [] (redirect (quickbooks/get-login-url)))
  (GET "/logged-in" [realmId code :as r]
       (let [login-info (quickbooks/get-id-token code)
             session (assoc (:session r) :user-id login-info)]
         {:status 200
          :headers {"Content-Type" "text/html"}
          :cookies {"hey" {:value "chavis"}}
          :session session
          :body (views/logged-in session)}))
  (GET "/connect-to-quickbooks" [] (redirect (quickbooks/get-authorization-url)))
  (GET "/connected-to-quickbooks" [realmId code :as r] (views/connected-to-quickbooks (:session r) code realmId))
  (GET "/privacy" request  (views/privacy))
  (GET "/export-auth" [] (redirect (quickbooks/get-authorization-url)))
  (GET "/export-data" [realmId code] (views/export-data-page realmId code))
  (POST "/export-data" {form-params :params} (views/export-data-results-page form-params))
  (route/not-found "Not Found"))

(def config (-> site-defaults
               (assoc-in [:session :store] (cookie-store {:key "abcdefghijklmnop"}))
               (assoc-in [:session :flash] false)
               (assoc-in [:session :cookie-attrs] {:http-only false})))
(println "config" config)

(def app (wrap-defaults app-routes config))

(defn get-port []
  (or (Integer/parseInt (env :port)) 3000))

(defn -main [& args]
  (let [port (get-port)]
    (run-jetty app {:port port})))

