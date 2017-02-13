(ns tracebackfilter.core
  (:import (java.io RandomAccessFile))
  (:require [clojure.string :refer [join starts-with? split]]
            [amazonica.aws.sns :as sns]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [clj-time.core :as -time]
            [clj-time.format :as format-time])
  (:gen-class))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Environment Variable Configs
(defn slack-sns-topic []
  (System/getenv "TBF_SNS_TOPIC"))

(defn slack-sns-subject []
  (System/getenv "TBF_SNS_SUBJECT"))


;;;;;;;;;;;;;;;;;;;;;
;; Sequence Functions

(defn drop-to-first
  "Returns a lazy sequence of successive items from coll after
  the point and including the point at which (pred item) returns true.
  pred must be free of side-effects."
  [before pred coll]
  (lazy-seq
    (when-let [s (seq coll)]
      (if-not (pred (nth s before nil))  ;; acts as a "lookahead" for retaining the before lines
        (drop-to-first before pred (rest s))  ;; move one ahead
        s))))

(defn take-to-first
  "Returns a lazy sequence of successive items from coll up to
  and including the point at which it (pred item) returns true.
  pred must be free of side-effects.
  taken from: https://groups.google.com/forum/#!topic/clojure/Gs6UtrRSLv8"
  [offset after pred coll]
  (when-let [s (seq coll)]
    (if (> offset 0)
      (let [o (take offset s)
            s (drop offset s)
            t (first s)]
        (if-not (pred t)
          (concat o [t] (take-to-first 0 after pred (rest s)))
          (take (inc after) s)))
      (let [t (first s)]
        (if-not (pred t)
          (concat [t] (take-to-first 0 after pred (rest s)))
          (take (inc after) s))))))


(defn partition-inside
  "Grabs whats between and including the pred-start and pred-stop.
  pred-start and pred-stop should not be the same predicate.
  if the data is of a 'nested' format each of the nestings will be returned."
  [before after pred-start pred-stop coll]
  (when-let [s (seq coll)]
    (lazy-seq
      (let [captured (->> s
                         (drop-to-first before pred-start)
                         (take-to-first before after pred-stop))
            res (drop (count (take-to-first before before pred-start s)) s)]
        (remove nil? (cons captured (partition-inside before after pred-start pred-stop res)))))))

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
  (if-not (nil? s)
    (if negative
      (nil? (re-find regex s))
      (not (nil? (re-find regex s))))
    false))

(def start-capture
  "Start capturing data after this if it returns true."
  (partial capture #"^Traceback" false))

(def end-capture
  "Stop capturing data if we get a line that doesn't match this regex."
  (partial capture #"^\s+|^Traceback|^[\w\.]+:\s+|^DETAIL|^$|^Attempt \d+ failed" true))

(defn extract-lines [before after input]
  "Given a streaming input (a log), outputs only what's found between
  and including the 'fencepost' predicates start-capture and end-capture."
  (partition-inside before after start-capture end-capture input))

;;;;;;;;;;;;;;;;;;
;; Subject Builder

(defn traceback-type
  "Extract the traceback type based on regex from a traceback."
  [coll]
  (first (filter #(re-find #"^[a-zA-Z_.]+:\s+" %) coll)))

(defn subject
  "Build the message subject from a prefix and a traceback."
  [s coll]
  (str s " - " (traceback-type coll)))

;;;;;;;;;;;;;;;;;;;;
;; Command & Control
(defn timestamp!
  "Get a human readable timestamp local to the box."
  []
  (format-time/unparse (format-time/formatter "yyyy-MM-dd HH:mm:ss,SSS") (-time/now)))

(defn ->log!
  "Send a message to STDOUT as a log message"
  [sub message]
  (log/info (str "------------------------------------------------------------------\n"
                 (timestamp!) "\n"
                 sub ":\n"
                 message)))

(defn ->sns!
  "Send a message to SNS."
  [arn sub message]
  (sns/publish :topic-arn arn
               :subject sub
               :message (str (timestamp!) "\n" message))
  (log/info (str "Traceback sent to " arn " with subject: " sub)))

(defn data->
  "Based on provided info, grab tracebacks and send them to the endpoint (log or SNS)"
  ([subject-prefix filename before after]  ;; useful for testing purposes, just send tracebacks to log/info
   (data-> ->log! subject-prefix filename before after ""))
  ([subject-prefix filename arn before after]  ;; send tracebacks to the sns topic
   (log/info (str "Sending tracebacks to SNS Topic: " arn
                  ", Subject Prefix: " subject-prefix))
   (data-> (partial ->sns! arn) subject-prefix filename before after ""))
  ([out-fn subject-prefix filename before after _]
   (doseq
     [traceback (extract-lines before after (tail-seq filename))]
     (let [traceback (remove empty? traceback)  ;; tracebacks contain a number of unnecessary newlines
           sub (subject subject-prefix traceback)]
       (out-fn sub (join "\n" traceback))))))


(def cli-options
  [["-a" "--after LINES" "number of lines to capture after the traceback"
    :id :after
    :default 0
    :parse-fn #(Integer/parseInt %)]
   ["-b" "--before LINES" "number of lines to cpature before the traceback"
    :id :before
    :default 0
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])


(defn -main
  "Entrypoint
  Requires the filename of the log to tail as the first arg."
  [& args]
  (let [topic (slack-sns-topic)
        subject (slack-sns-subject)])

  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        topic (slack-sns-topic)
        subject (slack-sns-subject)]
    (cond
      (:help options) (log/info summary)
      errors (log/error (join "\n" errors))
      (empty? arguments) (log/error summary)
      :else (pmap #(data-> (str subject " (" %1 ")") %1 topic (:before options) (:after options)) arguments))))

;;;;;;;;;;;;;;;;;;;;
;; Helpful Dev Stuff
(comment
  (clojure.pprint/pprint
    (extract-lines 0 0 ["Traceback (most recent call last):"
                        "  File \"./send_status.py\", line 227, in <module>"
                        "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                        ""
                        "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                        "[skipping] Running report"]))

  (clojure.pprint/pprint
    (extract-lines 0 0 ["Traceback (most recent call last):"
                        "  File \"./send_status.py\", line 227, in <module>"
                        "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                        ""
                        "Attempt 1 failed! Trying again in 4 seconds..."
                        "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                        "[skipping] Running report"]))

  (clojure.pprint/pprint
    (extract-lines 0 0 ["Traceback (most recent call last):"
                        "  File \"./send_status.py\", line 227, in <module>"
                        "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                        ""
                        "Attempt 1 failed and there are no more attempts left!"
                        "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                        "[skipping] Running report"]))

  (data-> "testing" "dev-resources/test.log" 2 2))
