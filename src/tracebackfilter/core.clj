(ns tracebackfilter.core
  (:import (java.io RandomAccessFile))
  (:require [clojure.string :refer [join starts-with?]]
            [amazonica.aws.sns :as sns]
            [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))


;;;;;;;;;;;;;;;;;;;;;
;; Sequence Functions

(defn take-to-first
  "Returns a lazy sequence of successive items from coll up to
  and including the point at which it (pred item) returns true.
  pred must be free of side-effects.
  taken from: https://groups.google.com/forum/#!topic/clojure/Gs6UtrRSLv8"
  [pred coll]
  (lazy-seq
   (when-let [s (seq coll)]
       (if-not (pred (first s))
         (cons (first s) (take-to-first pred (rest s)))
         (list (first s))))))

(defn partition-when
  "Applies f to each value in coll, splitting it each time f returns
   true. Returns a lazy seq of lazy seqs.
  taken from: https://groups.google.com/forum/#!topic/clojure/Gs6UtrRSLv8"
  [f coll]
  (when-let [s (seq coll)]
    (lazy-seq
      (let [run (take-to-first f s)
            res (drop (count run) s)]
          (cons run (partition-when f res))))))


(defn drop-to-first
  "Returns a lazy sequence of successive items from coll after
  the point and including the point at which (pred item) returns true.
  pred must be free of side-effects."
  [pred coll]
  (lazy-seq
    (when-let [s (seq coll)]
      (if-not (pred (first s))
        (drop-to-first pred (rest s))
        s))))

(defn partition-inside
  "Grabs whats between and including the pred-start and pred-stop.
  pred-start and pred-stop should not be the same predicate.
  if the data is of a 'nested' format and pred-start can happen multiple times before a pred-stop,
  unpredicatble behavior may occur.  I have not tested for this condition."
  [pred-start pred-stop coll]
  (when-let [s (seq coll)]
    (lazy-seq
      (let [run (take-to-first pred-stop (drop-to-first pred-start s))
            res (drop (count (take-to-first pred-start s)) s)]
        (cons run (partition-inside pred-start pred-stop res))))))


;;;;;;;;;;;;;;
;; File access

(defn raf-seq
  "Tail the provided file."
  [#^RandomAccessFile raf]
  (if-let [line (.readLine raf)]
    (lazy-seq (cons line (raf-seq raf)))
    (do (Thread/sleep 1000)
        (if (> (.getFilePointer raf) (.length raf))  ;; detect if the file has been truncated
          (.seek raf 0))
        (recur raf))))

(defn tail-seq
  "Seek the end of the input file and start tailing it."
  [input]
  (let [raf (RandomAccessFile. input "r")]
    (.seek raf (.length raf))
    (raf-seq raf)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Traceback "fencepost" predicates

(defn traceback-start?
  "Returns true when we start a traceback."
  [v]
  (starts-with? v "Traceback"))

(defn traceback-end?
  "Returns true once we're no longer in a traceback.
  Also attempts to capture details from psycopg2 tracebacks which are grungier."
  [v]
  (and
    (not (starts-with? v " "))
    (not (traceback-start? v))
    (not (starts-with? v "psycopg2"))
    (not (starts-with? v "DETAIL"))))



(defn extract-traceback [input]
  "Given a streaming input (a log), outputs only the tracebacks."
  (partition-inside traceback-start? traceback-end? input))


(defn slack-sns-topic []
  (System/getenv "TBF_SNS_TOPIC"))

(defn slack-sns-subject []
  (System/getenv "TBF_SNS_SUBJECT"))

(defn traceback-from-file [topic subject filename]
  "Given a filename, push the text of any tracebacks to the SNS topic."
  (doseq
    [traceback (extract-traceback (tail-seq filename))]
    (sns/publish :topic-arn topic
                 :subject (str subject " - " (last traceback))
                 :message (join "\n" traceback))))


(defn -main
  [& args]
  (let [topic (slack-sns-topic)
        subject (slack-sns-subject)]
    (println (str "Sending tracebacks from " (first args)
                  " to SNS Topic: " topic
                  ", Subject Prefix: " subject))
    (traceback-from-file topic subject (first args))))
