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
    (.close raf)
    (let [raf (RandomAccessFile. input-filename "r")]
      (if (> offset (.length raf))  ;; detect if the file's been truncated
        (.seek raf 0)
        (.seek raf offset))
      raf)))

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

(defn capture
  "Given a regex, exclude or include based on value of negative
  and whether the regex returns a value after being run over s."
  [regex negative s]
  (if negative
    (nil? (re-find regex s))
    (not (nil? (re-find regex s)))))

(def start-capture
  "Start capturing data after this if it returns true."
  (partial capture #"^Traceback" false))

(def end-capture
  "Stop capturing data if we get a line that doesn't match this regex."
  (partial capture #"^\s+|^Traceback|^[\w\.]+:\s+|^DETAIL|^$|^Attempt \d+ failed" true))

(defn extract-lines [input]
  "Given a streaming input (a log), outputs only what's found between
  and including the 'fencepost' predicates start-capture and end-capture."
  (partition-inside start-capture end-capture input))


;;;;;;;;;;;;;;;;;;
;; Publish Filters

(defn unsuccessful-retry?
  "Filter out any successful retries."
  [coll]
  (nil?
    (first
      (remove
        #(nil? (re-find #"Attempt \d+ failed! Trying again in \d+ seconds..." %))
        coll))))

;;;;;;;;;;;;;;;;;;
;; Subject Builder

(defn traceback-type
  "Extract the traceback type based on regex from a traceback."
  [coll]
  (first (filter #(re-find #"[\w\.]+:\s+" %) coll)))

(defn subject
  "Build the message subject from a prefix and a traceback."
  [s coll]
  (str s " - " (traceback-type coll)))

;;;;;;;;;;;;;;;;;;;;
;; Command & Control
(defn ->log!
  "Send a message to STDOUT as a log message"
  [sub message _]
  (log/info (str "------------------------------------------------------------------\n"
                 sub ":\n"
                 message)))

(defn ->sns!
  "Send a message to SNS."
  [arn sub message]
  (sns/publish :topic-arn arn
               :subject subject
               :message message)
  (log/info (str "Traceback sent to " arn " with subject: " sub)))

(defn data->
  "Based on provided info, grab tracebacks and send them to the endpoint (log or SNS)"
  ([subject-prefix filename]
   (data-> ->log! subject-prefix filename ""))
  ([subject-prefix filename arn]
   (data-> (partial ->sns! arn) subject-prefix filename ""))
  ([out-fn subject-prefix filename _]
   (doseq
     [traceback (filter unsuccessful-retry? (extract-lines (tail-seq filename)))]  ;; filter for tracebacks that weren't successfully retried
     (let [traceback (butlast (remove empty? traceback))  ;; tracebacks contain a number of unnecessary newlines and the final line is bunk
           sub (subject subject-prefix traceback)]
       (out-fn sub (join "\n" traceback))))))

(defn -main
  "Entrypoint
  Requires the filename of the log to tail as the first arg."
  [& args]
  (let [topic (slack-sns-topic)
        subject (slack-sns-subject)]
    (log/info (str "Sending tracebacks to SNS Topic: " topic
                   ", Subject Prefix: " subject))
    (data-> subject (first args) topic)))


;;;;;;;;;;;;;;;;;;;;
;; Helpful Dev Stuff
(comment
  (clojure.pprint/pprint
    (filter unsuccessful-retry?
      (butlast
        (extract-lines ["Traceback (most recent call last):"
                        "  File \"./send_status.py\", line 227, in <module>"
                        "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                        ""
                        "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                        "[skipping] Running report"]))))

  (clojure.pprint/pprint
    (filter unsuccessful-retry?
      (butlast
        (extract-lines ["Traceback (most recent call last):"
                        "  File \"./send_status.py\", line 227, in <module>"
                        "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                        ""
                        "Attempt 1 failed! Trying again in 4 seconds..."
                        "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                        "[skipping] Running report"]))))

  (clojure.pprint/pprint
    (filter unsuccessful-retry?
      (butlast
        (extract-lines ["Traceback (most recent call last):"
                        "  File \"./send_status.py\", line 227, in <module>"
                        "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                        ""
                        "Attempt 1 failed and there are no more attempts left!"
                        "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                        "[skipping] Running report"]))))

  (data-> "testing" "dev-resources/test.log"))
