(ns mergify.core
  (:require ["xhr2" :as xhr2]
            [cljs-http.client :as http]
            [clojure.core.async :refer [take!]]
            [re-lambda.core :refer [create-event-handler]]
            [utils.core :refer [env]]))
(set! js/XMLHttpRequest xhr2) ;; Fix cljs-http.client nodejs incompatibility

(defn get-mergify-api-key []
  (or (get (env) "MERGIFY_API_KEY") (throw "Missing MERGIFY_API_KEY environment variable")))

(defn parse-response [_event _response {:keys [http-delete http-put]}]
  (or http-delete http-put))

(comment (parse-response {} {} {:http-put {:status 200}}))

(defn mergify-freeze-low-queue-handler [reason mergify-api-key]
  (let [url "https://api.mergify.com/v1/repos/janeapp/jane/queue/low/freeze"
        options {:headers {"Content-Type" "application/json",
                           "Authorization" (str "Bearer " mergify-api-key)}
                 :form-params {:reason reason
                               :cascading true}}]
    {:http-put [url options]}))
(comment (mergify-freeze-low-queue-handler "testing" "ABC"))

(def low-queue-freeze (create-event-handler
                       mergify-freeze-low-queue-handler
                       {:co-effects [get-mergify-api-key]
                        :side-effects {:http-put http/put}
                        :parser parse-response}))

(defn mergify-unfreeze-low-queue-handler
  ([_event mergify-api-key] (mergify-unfreeze-low-queue-handler mergify-api-key))
  ([mergify-api-key]
   (let [url "https://api.mergify.com/v1/repos/janeapp/jane/queue/low/freeze"
         base-request {:as :text
                       :headers {"Content-Type" "application/json",
                                 "Authorization" (str "Bearer " mergify-api-key)}}]
     {:http-delete [url base-request]})))

(comment (mergify-unfreeze-low-queue-handler "" "ABC"))

(def low-queue-unfreeze (create-event-handler
                         mergify-unfreeze-low-queue-handler
                         {:co-effects [get-mergify-api-key]
                          :side-effects {:http-delete http/delete}
                          :parser parse-response}))

(defn main []
  (take! (low-queue-unfreeze "" (get-mergify-api-key)) println))
(comment (main))