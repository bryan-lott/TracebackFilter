(ns tracebackfilter.core-test
  (:require [clojure.test :refer :all]
            [tracebackfilter.core :refer :all]))

(def real-log ["Line 1 before"
               "Line 2 before"
               "Line 3 before"
               "Traceback (most recent call last):"
               "  File \"./send_status.py\", line 227, in <module>"
               "smtplib.SMTPDataError: (454, 'Temporary service failure')"
               ""
               "Attempt 1 failed! Trying again in 4 seconds..."
               "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
               "[skipping] Running report"
               "Line 1 after"
               "Line 2 after"
               "Line 3 after"])

(deftest test-take-to-first
  (testing "Found a split point"
    (is (= [1 1 1 1 2]
           (take-to-first 0 0 even? [1 1 1 1 2])))
    (is (= [1 1 2]
           (take-to-first 0 0 even? [1 1 2 1 1])))
    (is (= [2]
           (take-to-first 0 0 even? [2 1 1]))))
  (testing "No split point"
    (is (= [1 1 1]
           (take-to-first 0 0 even? [1 1 1]))))
  (testing "Capture n after"
    (is (= [1 1 1 1 2 1 1]
           (take-to-first 0 2 even? [1 1 1 1 2 1 1 1 1])))))

(deftest test-drop-to-first
  (testing "Drop point"
    (is (= [2 1 1]
           (drop-to-first 0 even? [1 1 2 1 1])))
    (is (= [2 1 1]
           (drop-to-first 0 even? [2 1 1])))
    (is (= [2]
           (drop-to-first 0 even? [1 1 2]))))
  (testing "No drop point"
    (is (= []
           (drop-to-first 0 even? [1 1 1])))
    (is (= []
           (drop-to-first 0 even? []))))
  (testing "Capture n before"
    (is (= [1 1 2 1 1]
           (drop-to-first 2 even? [1 1 1 2 1 1])))
    (is (= [1 2 1 1]
           (drop-to-first 1 even? [1 1 1 2 1 1])))
    (is (= [1 1 1 2 1 1]
           (drop-to-first 3 even? [1 1 1 2 1 1])))))

(deftest test-partition-inside
  (testing "Start/stop"
    (is (= [[true false]]
           (partition-inside 0 0 true? false? [true false])))
    (is (= [[true nil false]]
           (partition-inside 0 0 true? false? [nil true nil false nil]))))
  (testing "Multiple start/stop"
    (is (= [[true nil nil false] [true nil false]]
           (partition-inside 0 0 true? false? [nil true nil nil false nil nil true nil false nil]))))
  (testing "No start/stop"
    (is (= []
           (partition-inside 0 0 true? false? [nil nil nil])))
    (is (nil? (partition-inside 0 0 true? false? []))))
  (testing "Start no stop"
    (is (= [[true nil nil]]
           (partition-inside 0 0 true? false? [nil true nil nil]))))
  (testing "Nested - these are weird..."
    (is (= [[true nil true false] [true false]]
           (partition-inside 0 0 true? false? [nil true nil true false false])));])))
    (is (= [[true nil nil true nil false] [true nil false]]
           (partition-inside 0 0 true? false? [nil true nil nil true nil false nil false nil]))))
  (testing "Capture n elements after data"
    (is (= [[true nil false nil nil]]
           (partition-inside 0 2 true? false? [nil nil nil nil true nil false nil nil nil]))))

  (testing "Capture n elements before data"
    (is (= [[nil nil true nil false]]
           (partition-inside 2 0 true? false? [nil nil nil nil true nil false nil nil nil]))))
  (testing "Capture n elements before, m elements after data"
    (is (= [[nil nil true nil false nil nil]]
           (partition-inside 2 2 true? false? [nil nil nil nil true nil false nil nil nil])))
    (is (= [[nil true nil false nil nil]]
           (partition-inside 1 2 true? false? [nil nil nil nil true nil false nil nil nil])))))

(deftest test-start-capture
  (testing "Start capture"
    (is (true? (start-capture "Traceback"))))
  (testing "Don't start capture"
    (is (false? (start-capture "")))
    (is (false? (start-capture "traceback")))
    (is (false? (start-capture " ")))
    (is (false? (start-capture "other message")))))

(deftest test-end-capture
  (testing "End capture"
    (is (true? (end-capture "didn't start with a space or another keyword"))))
  (testing "Don't end capture"
    (is (false? (end-capture " starting with spaces")))
    (is (false? (end-capture "Traceback")))
    (is (false? (end-capture "smtplib.SMTPDataError: (454, 'Temporary service failure')")))
    (is (false? (end-capture "DETAIL: this is an AWS style traceback with extra redshift detail")))
    (is (false? (end-capture "")))
    (is (false? (end-capture "Attempt 12 failed")))))

(deftest test-extract-lines
  (testing "Traceback Exists"
    (is (= [["Traceback (most recent call last):"
             "  File \"./send_status.py\", line 227, in <module>"
             "smtplib.SMTPDataError: (454, 'Temporary service failure')"
             ""
             "Attempt 1 failed! Trying again in 4 seconds..."
             "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"]]
           (extract-lines 0 0 real-log))))
  (testing "No traceback"
    (is (= []
           (extract-lines 0 0 (map str (range 10))))))
  (testing "Capture N elements after"
    (is (= [["Traceback (most recent call last):"
             "  File \"./send_status.py\", line 227, in <module>"
             "smtplib.SMTPDataError: (454, 'Temporary service failure')"
             ""
             "Attempt 1 failed! Trying again in 4 seconds..."
             "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
             "[skipping] Running report"
             "Line 1 after"]]
           (extract-lines 0 2 real-log))))
  (testing "Capture N elements before"
    (is (= [["Line 2 before"
             "Line 3 before"
             "Traceback (most recent call last):"
             "  File \"./send_status.py\", line 227, in <module>"
             "smtplib.SMTPDataError: (454, 'Temporary service failure')"
             ""
             "Attempt 1 failed! Trying again in 4 seconds..."
             "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"]]
           (extract-lines 2 0 real-log))))
  (testing "Capture N elements before and after"
    (is (= [["Line 2 before"
             "Line 3 before"
             "Traceback (most recent call last):"
             "  File \"./send_status.py\", line 227, in <module>"
             "smtplib.SMTPDataError: (454, 'Temporary service failure')"
             ""
             "Attempt 1 failed! Trying again in 4 seconds..."
             "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
             "[skipping] Running report"
             "Line 1 after"]]
           (extract-lines 2 2 real-log)))))

(deftest test-traceback-type
  (testing "Extract traceback type"
    (is (= "traceback.Thing: "
           (traceback-type ["1" "2" "3" "traceback.Thing: "]))))
  (testing "No traceback found"
    (is (nil? (traceback-type ["1" "2"])))))

(deftest test-subject
  (testing "Building traceback subject"
    (is (= "Subject - traceback.Thing: "
           (subject "Subject" ["1" "2" "3" "traceback.Thing: "])))
    (is (= "Subject - traceback: "
           (subject "Subject" ["1" "2" "3" "traceback: "]))))
  (testing "No traceback found"
    (is (= "Subject - "
           (subject "Subject" ["1" "2"])))))

(deftest test-traceback-type
  (testing "Subject exists"
    (is (= "ConnectionClosedError: Connection was closed before we received a valid response from endpoint URL: \"https://s3bucket/data.tsv?partNumber=85&uploadId=some-base-64-encoded-string\"."
           (traceback-type ["Lines before"
                            "more lines before"
                            "ConnectionClosedError: Connection was closed before we received a valid response from endpoint URL: \"https://s3bucket/data.tsv?partNumber=85&uploadId=some-base-64-encoded-string\"."
                            "Lines after"
                            "More lines after"]))))
  (testing "Only grab the first"
    (is (= "ConnectionClosedError: Connection was closed before we received a valid response from endpoint URL: \"https://s3bucket/data.tsv?partNumber=85&uploadId=some-base-64-encoded-string\"."
           (traceback-type ["Lines before"
                            "more lines before"
                            "ConnectionClosedError: Connection was closed before we received a valid response from endpoint URL: \"https://s3bucket/data.tsv?partNumber=85&uploadId=some-base-64-encoded-string\"."
                            "psycopg2.DatabaseError: SSL SYSCALL error: EOF detected"
                            "Lines after"
                            "More lines after"])))))
