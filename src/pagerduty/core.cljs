(ns pagerduty.core
  (:require ["xhr2" :as xhr2]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]
            [re-lambda.core :refer [create-event-handler]]
            [utils.core :refer [env]]))

(set! js/XMLHttpRequest xhr2) ;; Fix cljs-http.client nodejs incompatibility

(defn pagerduty-handler [services pagerduty-api-key]
  (let [url "https://api.pagerduty.com/incidents"
        query-params {"statuses[]" ["triggered" "acknowledged"]
                      "service_ids[]" services}
        options {:as :text
                 :query-params query-params
                 :headers {"Authorization" (str "Token token=" pagerduty-api-key)}}]
    {:http-incidents [url options]}))

(comment (http/get (pagerduty-handler ["service-id-1" "service-id-2" "service-id-3"] "ABC")))
(defn parse-incidents [_event _response side-effects]
  (let [{:keys [http-incidents]} side-effects
        response http-incidents]
    (get-in response [:body :incidents])))

(comment (parse-incidents {} {} {:http-incidents {:body {:incidents []}}}))
          

(defn get-pagerduty-api-key []
  (or (get env "PAGERDUTY_API_KEY") "ABC"))

(def fetch-incidents (create-event-handler
                      pagerduty-handler
                      {:co-effects [get-pagerduty-api-key]
                       :side-effects {:http-incidents http/get}
                       :parser parse-incidents}))

(comment (fetch-incidents ["service-id-1" "service-id-2" "service-id-3"]))

(defn main [services]
  (go (-> services
          fetch-incidents
          <!
          println)))

(set! js/XMLHttpRequest xhr2)
(comment (main ["PLCR373" "PE7V774" "PVP8PO3"]))