(ns re-lambda.core
  (:require [cljs.core.async.impl.channels :refer [ManyToManyChannel]]
            [cljs.core.async.impl.protocols :refer [Channel]]
            [clojure.core.async :as a :refer [<! >! go]]
            [utils.core :refer [clj->json]]))


(defn ring->apigw [response]
  (clj->js
   {:statusCode (:status response)
    :body (-> response :body clj->json)}))

(defn channel?
  [x]
  (or (satisfies? Channel x)
      (satisfies? ManyToManyChannel x)))


(defn apply-async [[key [side-effect args]] channel]
  (go
    (let [work (apply side-effect args)
          resolved-work (if (channel? work)
                          (<! work)
                          work)]
      ;; (println "===========")
      ;; (println side-effect args)
      ;; (println resolved-work) 
      (>! channel {key resolved-work})
      (a/close! channel))))

(defn apply-side-effects
  [side-effects response]
  (let [side-effects-count (count response)
        channel (a/chan side-effects-count)
        zip-with-side-effects (fn [[key args]]
                                (let [side-effect (key side-effects)]
                                  (when side-effect
                                    [key [side-effect args]])))] 
    (go
      (cond
        (= side-effects-count 0) (do 
                                   (>! channel {})
                                   (a/close! channel))
        :else (a/pipeline-async
               side-effects-count ;; parallel
               channel          ;; output chanel
               apply-async      ;; map function
               (a/to-chan! (map zip-with-side-effects response))))) ;; input channel
    channel))

(defn apply-co-effects
  [co-effects]
  (let [channel (a/chan (count co-effects))]
    (go
      (doseq [co-effect co-effects]
        (let [res (if (fn? co-effect)
                    (co-effect)
                    co-effect)
              resolved-res (if (channel? res) (<! res) res)]
          (>! channel resolved-res)))
      (a/close! channel))
    channel))

(defn create-event-handler
  [handler {:keys [co-effects
                   side-effects
                   parser]
            :or {co-effects []
                 side-effects {}
                 parser (fn [_event response _side-effects-results] response)}}]
  (fn [event & [_context callback]]
    (go
      (let [co-effects-channel (apply-co-effects co-effects)
            response (apply handler event (<! (a/into [] co-effects-channel)))
            side-effect-channel (apply-side-effects side-effects response)
            res (parser event response (<! (a/into {} side-effect-channel)))]
        (when (some? callback) (callback nil (cond
                                               (contains? res :body) (ring->apigw res)
                                               :else res)))
        res))))
