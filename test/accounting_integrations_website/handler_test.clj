(ns accounting-integrations-website.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [accounting-integrations-website.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (= (:body response) "Home"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
