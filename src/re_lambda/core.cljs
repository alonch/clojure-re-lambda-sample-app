(ns re-lambda.core
  (:require [cljs.core.async.impl.channels :refer [ManyToManyChannel]]
            [cljs.core.async.impl.protocols :refer [Channel]]
            [clojure.core.async :as a :refer [<! >! go]]))


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
      (>! channel {key resolved-work})
      (a/close! channel))))

(defn apply-side-effects
  [side-effects response]
  (let [channel (a/chan (count response))
        zip-with-side-effects (fn [[key args]]
                                (let [side-effect (key side-effects)]
                                  (when side-effect
                                    [key [side-effect args]])))]
    (go (a/pipeline-async
         (count response) ;; parallel
         channel          ;; output chanel
         apply-async      ;; map function
         (a/to-chan! (map zip-with-side-effects response)))) ;; input channel
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
  (fn [& [event]]
    (go
      (let [co-effects-channel (apply-co-effects co-effects)
            response (apply handler event (<! (a/into [] co-effects-channel)))
            side-effect-channel (apply-side-effects side-effects response)
            res (parser event response (<! (a/into {} side-effect-channel)))]
        res))))