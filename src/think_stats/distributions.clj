(ns think-stats.distributions
  (:refer-clojure :exclude [partition])
  (:require (think-stats
              [util :as util]
              [stats :as stats]
              [homeless :as h])))


(defn percentile
  "A more efficient percentile that uses a selection algorithm
  http://en.wikipedia.org/wiki/Selection_algorithm. 

  \"More efficient\" should be taken with a grain of salt. YMMV and it probably depends on the data set.
  "
  [s k]
  (let [scaled-k (* (count s) (/ k 100))
        s (vec s)] 
    (h/select s 0 (dec (count s)) scaled-k)))

(defn percentile-rank
  [scores yours]
  (* (/ 
       (count (filter #(<= % yours) scores)) 
       (count scores)) 
    100.0))

(defn percentile-s
  "For illustration purposes."
  [scores rank]
  (let [scores (sort scores)
        len (count scores)
        rank (/ rank 100)]
    (loop [s scores
           current-rank 1]
      (if (>= (/ current-rank len) rank)
        (first s)
        (recur (rest s) (inc current-rank))))))

(defn percentile-w
  "Wikipedia implementation http://en.wikipedia.org/wiki/Percentile"
  [s x]
  (assert (sequential? s) "Cannot compute the percentile on a non-seq data set.")
  (let [s (sort s)
        len (count s)
        x (max 0 (min x 100))
        c (+ (* (/ x 100) len) 0.5)
        idx (dec c)] ; zero offset
    (nth s idx)))


(defn compute-cdf-value
  [s x]
  (assert (sequential? s) "Cannot compute the cdf on a non-seq data set.")
  (/ 
    (count (filter #(<= % x) s)) 
    (count s)))

(defn cdf
  [s]
  (assert (sequential? s) "Cannot compute the cdf on a non-seq data set.")
  (let [s (sort s)
        len (count s)
        m (into (sorted-map)
                (loop [s' s idx 1 acc []]
                  (if (empty? s')
                    acc
                    (recur (rest s') (inc idx) (conj acc [(first s') (/ idx len)])))))]
        m))

(defn- cdf->probability
  [kys vls x]
  (cond
    (< x (first kys)) 0
    (> x (last kys))  1
    :else 
    (let [kidx (h/bisect kys x :left)]
      (nth vls kidx))))

(defn- cdf->value
  [kys vls prob]
  (cond
    (< prob 0) nil
    (> prob 1) nil
    :else 
    (let [vidx (h/bisect vls prob :left)]
      (nth kys vidx))))


; should this be a protocol and a type?
(defn cdff
  "Returns a function (f x) that computes the CDF(x) = p and it's inverse from the data set s.

  (def cdf (cdff (range 1 101)))
  (cdf 10) => 0.1
  (cdf 10 :probability) => 0.1
  (cdf 0.1 :value) => 10

  "
  [s]
  (assert (sequential? s) "Cannot compute the cdf on a non-seq data set.")
  (let [m (cdf s)
        kys (keys m)
        vls (vals m)]
    (fn [x &[direction]]
      (if (and (not (nil? direction)) (= direction :value))
        (cdf->value kys vls x)
        (cdf->probability kys vls x)))))

(defn sample
  "Generate a lazy seq of values chosen at random from the given cdf. See cdff above.

  (def cdf (cdff (take 50 (repeatedly #(rand-int 10)))))
  (sample cdf 10)
  "
  [cdf n]
  (for [i (range n)]
    (cdf (rand) :value)))


      



