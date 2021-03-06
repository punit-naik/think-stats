(ns think-stats.pregnancy
  (:require (think-stats
              [util :as util]
              [homeless :as h]
              [hist :as hist]
              [stats :as stats]
              [survey :as s])))


(def fields [(s/def-field-extractor "caseid" 0 12)
             (s/def-field-extractor "nbrnaliv" 21 22)
             (s/def-field-extractor "babysex" 55 56)
             (s/def-field-extractor "birthwgt_lb" 56 58)
             (s/def-field-extractor "birthwgt_oz" 58 60)
             (s/def-field-extractor "prglength" 274 276)
             (s/def-field-extractor "outcome" 276 277)
             (s/def-field-extractor "birthord" 277 279)
             (s/def-field-extractor "agepreg" 283 287)
             (s/def-field-extractor "finalwgt" 422 440 util/str-to-float)])

(declare load-data)

(defn plot-length-hist
  [data-file &{:keys [csv-out r-script to-plot week-min week-max week-min week-max] 
               :or {csv-out "plots/2.1.csv" r-script "plots/2.1.R" to-plot "plots/2.1.png" week-min 0 week-max 99} 
               :as params}]
  (let [[first-babies other-babies] (map hist/hist (apply load-data data-file params))
        upper (+ (max (apply max (keys first-babies)) (apply max (keys other-babies))) 1)
        lower (min (apply min (keys first-babies)) (apply min (keys other-babies)))
        combined (concat (list (list "prglength" "first" "others")) 
                         (for [i (range lower upper)] (list i (get first-babies i 0) (get other-babies i 0))))]
    (util/write-to-csv csv-out combined)
    (util/shell-exec (format "Rscript %s %s %s" r-script csv-out to-plot))))

(defn plot-diff-hist
  [data-file &{:keys [csv-out r-script to-plot week-min week-max]
               :or {csv-out "plots/2.3.csv" r-script "plots/2.3.R" to-plot "plots/2.3.png" week-min 0 week-max 99} 
               :as params}]
  (let [[first-babies other-babies] (map hist/pmf (apply load-data data-file params))
        upper (+ (max (apply max (keys first-babies)) (apply max (keys other-babies))) 1)
        lower (min (apply min (keys first-babies)) (apply min (keys other-babies)))
        combined (concat (list (list "prglength" "difference"))
                         (for [i (range lower upper)] (list i (float
                                                                (* 100
                                                                  (-
                                                                    (get first-babies i 0)
                                                                    (get other-babies i 0)))))))]
    (util/write-to-csv csv-out combined)
    (util/shell-exec (format "Rscript %s %s %s" r-script csv-out to-plot))))



(defn on-time
  [pmf &{:keys [binfn] :or {binfn #{38 39 40}}}]
  (float (hist/bin-pmf-freq pmf binfn)))

(defn early
  [pmf &{:keys [binfn] :or {binfn (set (range 0 38))}}]
  (float (hist/bin-pmf-freq pmf binfn)))

(defn late
  [pmf &{:keys [binfn] :or {binfn (set (range 41 51))}}]
  (float (hist/bin-pmf-freq pmf binfn)))


(defn risk
  [data-file & params]
  (let [[first-babies other-babies live] (map (comp hist/pmf stats/trim) (apply load-data data-file params))]
    {:first
     {:on-time (on-time first-babies)
      :early (early first-babies)
      :late (late first-babies)}
     :other
     {:on-time (on-time other-babies)
      :early (early other-babies)
      :late (late other-babies)}
     :live
     {:on-time (on-time live)
      :early (early live)
      :late (late live)}}))

(defn plot-prob-week-x
  [data-file &{:keys [csv-out r-script to-plot week-min week-max]
               :or {csv-out "plots/2.7.csv" r-script "plots/2.7.R" to-plot "plots/2.7.png" week-min 27 week-max 44} 
               :as params}]
  (let [[first-babies other-babies live] (map (comp hist/pmf stats/trim) (apply load-data data-file params))
        week-max (+ week-max 1)
        generator (fn [data-set x] (get (hist/normalize-pmf (h/filter-map data-set (set (range x week-max)))) x 0))
        x (prn "inside " first-babies)
        x (prn "gen " (generator first-babies 0))
        rows (for [x (range week-min week-max)]
               [x
                (generator first-babies x)
                (generator other-babies x)
                (generator live x)])
        header (list "prglength" "first" "other" "live")
        combined (concat (list header) rows)]
    (util/write-to-csv csv-out combined)
    (util/shell-exec (format "Rscript %s %s %s" r-script csv-out to-plot))))

(defn recode
  "Recode pregancy fields."
  [r]
  (cond-> r
    (and (get r "birthwgt_lb")
         (< (get r "birthwgt_lb") 20)
         (get r "birthwgt_oz")
         (< (get r "birthwgt_oz") 16)) (assoc "totalwgt_oz"
                                              (+ (* (get r "birthwgt_lb") 16)
                                                 (get r "birthwgt_oz")))
    (get r "agepreg") (assoc "agepreg" (/ (get r "agepreg") 100.0))))

; FIXME: return the whole record not just the column. let another function filter out the column
(defn load-data
  ([data-file &{:keys [week-min week-max column] :or {week-min 0 week-max 99 column "prglength"} :as params}]
   (let [preg-data (util/read-file data-file :gunzip true)
         db (map (partial s/line->fields fields) preg-data)
         db (map recode db)
         predicate (fn [r]
                     ; we only want to process records that fall within what we believe are valid weeks
                     (when-let [len (get r "prglength")]
                       (and
                         (get r column)
                         (get r "birthord" nil)
                         (= (get r "outcome") 1) ; only live births
                         (>= len week-min)
                         (<= len week-max))))
         first-babies (for [r db :when (and (predicate r) (= (get r "birthord") 1))] (get r column))
         other-babies (for [r db :when (and (predicate r) (not= (get r "birthord") 1))] (get r column))
         live-births  (for [r db :when (and (predicate r) (= (get r "outcome") 1))] (get r column))]
     [first-babies other-babies live-births]))
  ([column]
   (load-data "data/2002FemPreg.dat.gz" :column column)))

(defn load-births
  ([data-file &{:keys [week-min week-max column] :or {week-min 0 week-max 99 column "prglength"} :as params}]
   (let [preg-data (util/read-file data-file :gunzip true)
         db (map (partial s/line->fields fields) preg-data)
         db (map recode db)
         predicate (fn [r]
                     (when-let [len (get r "prglength")]
                       (and
                         (get r "birthord" nil)
                         (= (get r "outcome") 1) ; only live births
                         (>= len week-min)
                         (<= len week-max))))
         live-births  (for [r db :when (and (predicate r))]
                        r)]
     live-births))
  ([]
   (load-births "data/2002FemPreg.dat.gz")))
