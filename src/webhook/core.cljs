(ns webhook.core
  (:require ["aws-xray-sdk-core" :as aws-x-ray]
            ["https" :as https]
            ["xhr2" :as xhr2]
            [clojure.core.async :as a]
            [mergify.core :as mergify]
            [pagerduty.core :as pagerduty]
            [re-lambda.core :as rl]))

(defn on-pagerduty-event [_event incidents]
  (let
   [incident-count (count incidents)
    reason (str "Freezing the queue due to " incident-count " active incidents on PagerDuty")
    initial-queue-state {:mergify-apply-queue-status [{}]}]
    (cond-> 
     initial-queue-state
     (> incident-count 0) (assoc :mergify-apply-queue-status [{:low reason}]))))

(comment (on-pagerduty-event {} [1 2 3]))
(comment (on-pagerduty-event {} []))

(defn parse-response [_event _response {:keys [mergify-apply-queue-status]}]
  {:status 200
   :body mergify-apply-queue-status})

(comment (parse-response {} {} {:mergify-freeze {:status 404 :body {:details "hello world"}}}))

(defn pagerduty-incidents []
  (pagerduty/fetch-incidents ["PLCR373" "PE7V774" "PVP8PO3"]))

(def on-indendent-webhook (do
                            (. aws-x-ray captureHTTPsGlobal https)
                            (set! js/XMLHttpRequest xhr2)
                            (rl/create-event-handler
                             on-pagerduty-event
                             {:co-effects [pagerduty-incidents]
                              :side-effects {:mergify-apply-queue-status mergify/apply-queue-status}
                              :parser parse-response})))
