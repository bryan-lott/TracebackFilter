(ns tracebackfilter.core
  (:import (java.io RandomAccessFile))
  (:require [clojure.string :refer [join starts-with? split]]
            [amazonica.aws.sns :as sns]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log])
  (:gen-class))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment Variable Configs
(defn slack-sns-topic []
  (System/getenv "TBF_SNS_TOPIC"))

(defn slack-sns-subject []
  (System/getenv "TBF_SNS_SUBJECT"))


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
  if the data is of a 'nested' format each of the nestings will be returned."
  [pred-start pred-stop coll]
  (when-let [s (seq coll)]
    (lazy-seq
      (let [run (take-to-first pred-stop (drop-to-first pred-start s))
            res (drop (count (take-to-first pred-start s)) s)]
          (cons run (partition-inside pred-start pred-stop res))))))


;;;;;;;;;;;;;;
;; File access
(defn reopen [input-filename raf]
  "Close and reopen the provided file.
  This is to be able to follow logrotate when it switches."
  (let [offset (.getFilePointer raf)]
    (do
      (.close raf)
      (let [raf (RandomAccessFile. input-filename "r")]
        (if (> offset (.length raf))  ;; detect if the file's been truncated
          (.seek raf 0)
          (.seek raf offset))
        raf))))

(defn raf-seq
  "Tail the provided file."
  [input-filename #^RandomAccessFile raf]
  (if-let [line (.readLine raf)]
    (lazy-seq (cons line (raf-seq input-filename raf)))
    (do (Thread/sleep 1000)
        (recur input-filename
               (reopen input-filename raf)))))

(defn tail-seq
  "Seek the end of the input file and start tailing it."
  [input-filename]
  (let [raf (RandomAccessFile. input-filename "r")]
    (.seek raf (.length raf))
    (raf-seq input-filename raf)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Traceback "fencepost" predicates

(defn traceback-start?
  "Returns true when we start a traceback."
  [v]
  (starts-with? v "Traceback"))

(defn traceback-end?
  "Returns true once we're no longer in a traceback.
  Also attempts to capture details from AWS psycopg2 tracebacks which are grungier."
  [v]
  (and
    (not (starts-with? v " "))
    (not (traceback-start? v))
    (not (starts-with? v "psycopg2"))
    (not (starts-with? v "DETAIL"))
    (not (= v ""))
    (not (= v "\n"))
    (not (= v "\r\n"))))

;;;;;;;;;;;;;;;;;;;;
;; Command & Control
(defn extract-traceback [input]
  "Given a streaming input (a log), outputs only the tracebacks."
  (partition-inside traceback-start? traceback-end? input))

(defn traceback-from-file [topic subject filename]
  "Given a filename, push the text of any tracebacks to the SNS topic."
  (doseq
    [traceback (extract-traceback (tail-seq filename))]
    (let [sns-subject (str subject " - " (first (split (last traceback) #"\s")))] 
      (sns/publish :topic-arn topic
                  :subject sns-subject
                  :message (join "\n" traceback))
      (log/info (str "Traceback sent to " topic " with subject: " sns-subject)))))

(defn -main
  [& args]
  (let [topic (slack-sns-topic)
        subject (slack-sns-subject)]
    (log/info (str "Sending tracebacks to SNS Topic: " topic
                   ", Subject Prefix: " subject))
    (traceback-from-file topic subject (first args))))
