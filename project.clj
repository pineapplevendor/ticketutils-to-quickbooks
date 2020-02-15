(defproject accounting-integrations-website "0.1.0-SNAPSHOT"
  :description "A web app to transfer TicketUtils data to QuickBooks"
  :url "Nothing picked yet"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [prismatic/schema "1.1.12"]
                 [compojure "1.6.1"]
                 [buddy/buddy-core "1.6.0"]
                 [org.clojure/data.json "0.2.7"]
                 [clj-http "3.10.0"]
                 [environ "1.1.0"]
                 [com.intuit.quickbooks-online/oauth2-platform-api "5.0.2"]
                 [com.intuit.quickbooks-online/ipp-v3-java-data "5.0.2"]
                 [com.intuit.quickbooks-online/ipp-v3-java-devkit "5.0.2"]
                 [javax.xml.bind/jaxb-api "2.4.0-b180830.0359"]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring/ring-defaults "0.3.2"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler accounting-integrations-website.handler/app}
  :main accounting-integrations-website.handler
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}}
  :aot [accounting-integrations-website.handler])
