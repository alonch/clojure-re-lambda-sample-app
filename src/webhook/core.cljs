(ns webhook.core
  (:require ["xhr2" :as xhr2]
            [clojure.core.async :refer [take!]]
            [mergify.core :as mergify]
            [pagerduty.core :as pagerduty]
            [re-lambda.core :as rl]
            [utils.core :refer [clj->json]]))

 ;; Fix cljs-http.client nodejs incompatibility

(defn on-pagerduty-event [_event incidents]
  (let
   [incident-count (count incidents)
    reason (str "Freezing the queue due to " incident-count " active incidents on PagerDuty")]
    (if (> incident-count 0)
      {:mergify-freeze [reason]}
      {:mergify-unfreeze []})))

(comment (on-pagerduty-event {} [1 2 3]))
(comment (on-pagerduty-event {} []))

(defn parse-response [_event _response {:keys [mergify-freeze mergify-unfreeze]}]
  (let [result (or mergify-freeze mergify-unfreeze)]
    {:statusCode (:status result)
     :body (-> result
               :body
               clj->json)}))

(comment (parse-response {} {} {:mergify-freeze {:status 404 :body {:details "hello world"}}}))

(defn pagerduty-incidents []
  (pagerduty/fetch-incidents ["PLCR373" "PE7V774" "PVP8PO3"]))

(def on-indendent-webhook (rl/create-event-handler
                           on-pagerduty-event
                           {:co-effects [pagerduty-incidents]
                            :side-effects {:mergify-freeze mergify/low-queue-freeze
                                           :mergify-unfreeze mergify/low-queue-unfreeze}
                            :parser parse-response}))

(defn handler [event _context callback]
  (do
    (set! js/XMLHttpRequest xhr2)
    (take! (on-indendent-webhook event)
           #(callback nil (clj->js %)))))

(comment (handler {} {} println))