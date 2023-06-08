(ns re-lambda.core-test
  (:require [cljs.test :refer [async]]
            [clojure.core.async :refer [<! go]]
            [clojure.core.async :as a]
            [clojure.test :refer [deftest is testing]]
            [re-lambda.core :as rl]))

(deftest test-apply-co-effects
  (testing "apply co-effects return channel with resolved values"
    (async done
           (go
             (let [co-effects [#(go "hello")]
                   co-effects-channel (rl/apply-co-effects co-effects)
                   res (<! (a/into [] co-effects-channel))]
               (is (= res ["hello"]))
               (done))))))

(deftest test-apply-async
  (testing "apply-async fills the output channel with the resolved side-effects"
    (async done
           (go
             (let [channel (a/chan)
                   side-effect (fn [arg] (go arg))
                   _ (rl/apply-async [:hello [ side-effect ["world"]]] channel)
                   res (<! (a/into {} channel))]
               (is (= res
                      {:hello "world"}))
               (done))))))

(deftest test-apply-side-effects
  (testing "apply-side-effects returns all side-effects results as a dictionary"
    (async done
           (go
             (let [side-effects {:hello (fn [arg] (go arg))}
                   handler-response {:hello ["world"]}
                   resolved-response (rl/apply-side-effects side-effects handler-response)
                   res (<! resolved-response)]
               (is (= res
                      {:hello "world"}))
               (done))))))

