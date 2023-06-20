(ns mergify.core
  (:require ["xhr2" :as xhr2]
            [cljs-http.client :as http]
            [re-lambda.core :refer [create-event-handler]]
            [utils.core :refer [env]]
            [editscript.core :as e]))
(set! js/XMLHttpRequest xhr2) ;; Fix cljs-http.client nodejs incompatibility

(defn mergify-map-queue-response [body]
  (let [{:keys [queue_freezes]} body]
    (->> queue_freezes
         (map (fn [value] {(-> value :name keyword)
                           (:reason value)}))
         (into {}))))

(defn mergify-response-invalid-reason [status]
  (cond
    (= status 403) "Invalid Token while trying to get frozen queue status"
    (= status 442) "Validation Error trying to get frozen queue status"
    (= status 404) "Frozen queue not found"
    :else "Error on Mergify Servers while trying to get frozen queue status"))

(defn mergify-request [mergify-api-key queue-name]
  (let [url (str "https://api.mergify.com/v1/repos/janeapp/jane/queue/" (name queue-name) "/freeze")
        options {:headers {"Content-Type" "application/json",
                           "Authorization" (str "Bearer " mergify-api-key)}}]
    [url options]))

(defn mergify-request-freeze [mergify-api-key queue-name reason]
  (let [[url base-options] (mergify-request mergify-api-key queue-name)
        options (assoc base-options :form-params {:reason reason
                                                  :cascading true})]
    [url options]))

(comment (mergify-request-freeze "ABC" "low" "Ongoing Incident"))

(defn get-mergify-api-key []
  (or (get (env) "MERGIFY_API_KEY") (throw "Missing MERGIFY_API_KEY environment variable")))

(defn mergify-queue-freeze-status
  ([queue-name mergify-api-key]
   {:http-get (mergify-request mergify-api-key queue-name)}))

(comment (mergify-queue-freeze-status "low" "ABC"))

(defn parse-queue-status [queue-name _response {resp :http-get}]
  (let [{status :status
          body :body} resp]
    (println resp)
    (cond
      (= status 404) {}
      (= status 200) (mergify-map-queue-response body)
      :else (throw (ex-info (mergify-response-invalid-reason status)
                            (assoc body :queue queue-name))))))
  

(comment (parse-queue-status {} {} {:http-get {:status 200
                                               :body {:queue_freezes [{:name "low" :reason "Incident"}]}}}))

(comment (parse-queue-status {} {} {:http-get {:status 404}}))

(comment (parse-queue-status {} {} {:http-get {:status 500}}))

(def fetch-queue-freeze-status (create-event-handler
                                mergify-queue-freeze-status
                                {:co-effects [get-mergify-api-key]
                                 :side-effects {:http-get http/get}
                                 :parser parse-queue-status}))

(defn apply-queue-status-handler [decired-queues current-queues mergify-api-key]
  (let [wrapper {:title "frozen queues"} ;; to prevent editscript from replacing the whole map 
        queue-diff (e/diff (merge wrapper current-queues) (merge wrapper decired-queues))
        [[[queue] op reason]] (e/get-edits queue-diff)] ;; sample edit: [[:low] :+ "Incident in PD"]
    (cond-> {}
      ;; :- deletion
      (= :- op) (assoc :http-delete
                       (mergify-request mergify-api-key
                                        queue))
      ;; :r replacement
      ;; :+ addition
      (contains? #{:r :+} op) (assoc :http-put
                                     (mergify-request-freeze mergify-api-key
                                                             queue
                                                             reason)))))

(comment (apply-queue-status-handler {:low "hello"} {} "ABC")
         {:http-put
          ["https://api.mergify.com/v1/repos/janeapp/jane/queue/:low/freeze"
           {:headers {"Content-Type" "application/json", "Authorization" "Bearer ABC"},
            :form-params {:reason "hello", :cascading true}}]})

(comment (apply-queue-status-handler {} {:low "hello"} "ABC")
         {:http-delete
          ["https://api.mergify.com/v1/repos/janeapp/jane/queue/low/freeze"
           {:headers {"Content-Type" "application/json", "Authorization" "Bearer ABC"}}]})

(comment (apply-queue-status-handler {:low "hello world"} {:low "hello"} "ABC")
         {:http-put
          ["https://api.mergify.com/v1/repos/janeapp/jane/queue/low/freeze"
           {:headers {"Content-Type" "application/json", "Authorization" "Bearer ABC"},
            :form-params {:reason "hello world", :cascading true}}]})

(comment (apply-queue-status-handler {:low "hello world"} {:low "hello world"} "ABC")
         {})
 
(defn parse-apply-queue-status [[decired-queues current-queues _mergify-api-key] 
                                _response 
                                {resp-put :http-put resp-delete :http-delete}]
  (let [resp (or resp-put resp-delete)
        {status :status
         body :body} resp] 
    (cond
      (contains?  #{204 200} status) (mergify-map-queue-response body) 
      :else (throw (ex-info (mergify-response-invalid-reason status) 
                            {:body body 
                             :decired-queues decired-queues
                             :current-queues current-queues})))))

(def apply-queue-status (create-event-handler
                         apply-queue-status-handler
                         {:co-effects [#(fetch-queue-freeze-status "low") get-mergify-api-key]
                          :side-effects {:http-put http/put
                                         :http-delete http/delete}
                          :parser parse-apply-queue-status}))

(comment (apply-queue-status "low" nil {:http-put {:status 200
                                                   :body {:queue_freezes [{:name "low"
                                                                           :reason "Ongoing Incidents"}]}}}))