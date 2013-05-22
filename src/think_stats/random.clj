(ns think-stats.random
  (:require (think-stats
              [distributions :as d]
              [constants :as c]
              [homeless :as h]
              [stats :as stats]))
  (:import org.apache.commons.math3.special.Erf))

(declare normalvariate)

(def standard-normal-mu 0)
(def standard-normal-sigma 1)
(def standard-normalvariate #(normalvariate standard-normal-mu standard-normal-sigma))

(def rankit-items 6)


(defn sample
  "Sample f by calling it n times."
  [n f]
  (repeatedly n f))

(defn rankit-sample
  ([cdf]
   (into [] (sample rankit-items cdf)))
  ([]
   (rankit-sample standard-normalvariate)))

(defn rankit-samples
  ([cdf n]
   (map stats/mean
        (apply map vector
               (map sort
                    (repeatedly n #(rankit-sample cdf))))))
  ([n]
   (rankit-samples standard-normalvariate n)))



(defn normalvariate
  "Generate random values from a normal distribution with mean mu and standard deviation sigma."
  [mu sigma]
  (let [p (rand)
        x (* c/sqrt2 (Erf/erfInv (- (* 2 p) 1)))]
    (+ (* sigma x) mu)))


(defn expovariate
  "Generate random values from an exponential distribution with rate parameter lambda.
  See http://en.wikipedia.org/wiki/Exponential_distribution generating exponential variates"
  [lambda]
  (* -1.0 (/ (Math/log (- 1.0 (rand))) (float lambda))))


(defn expomedian
  "Compute the median of an exponential distribution with a given rate parameter lambda."
  [lambda]
  (/ (Math/log 2) lambda))


(defn expomean
  "Compute the mean of an exponential distribution with a given rate parameter lambda."
  [lambda]
  (/ 1.0 lambda))
