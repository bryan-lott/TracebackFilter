(ns tracebackfilter.core-test
  (:require [clojure.test :refer :all]
            [tracebackfilter.core :refer :all]))

(def real-log ["Traceback (most recent call last):"
               "  File \"./send_status.py\", line 227, in <module>"
               "smtplib.SMTPDataError: (454, 'Temporary service failure')"
               ""
               "Attempt 1 failed! Trying again in 4 seconds..."
               "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
               "[skipping] Running report"])

(deftest test-take-to-first
  (testing "Found a split point"
    (is (= [1 1 1 1 2]
           (take-to-first even? [1 1 1 1 2])))
    (is (= [1 1 2]
           (take-to-first even? [1 1 2 1 1])))
    (is (= [2]
           (take-to-first even? [2 1 1]))))
  (testing "No split point"
    (is (= []
           (take-to-first even? [])))
    (is (= [1 1 1]
           (take-to-first even? [1 1 1])))))

(deftest test-drop-to-first
  (testing "Drop point"
    (is (= [2 1 1]
           (drop-to-first even? [1 1 2 1 1])))
    (is (= [2 1 1]
           (drop-to-first even? [2 1 1])))
    (is (= [2]
           (drop-to-first even? [1 1 2]))))
  (testing "No drop point"
    (is (= []
           (drop-to-first even? [1 1 1])))
    (is (= []
           (drop-to-first even? [])))))

(deftest test-partition-inside
  (testing "Start/stop"
    (is (= [[true false] []]
           (partition-inside true? false? [true false])))
    (is (= [[true nil false] []]
           (partition-inside true? false? [nil true nil false nil]))))
  (testing "Multiple start/stop"
    (is (= [[true nil nil false] [true nil false] []]
           (partition-inside true? false? [nil true nil nil false nil nil true nil false nil]))))
  (testing "No start/stop"
    (is (= [[]]
           (partition-inside true? false? [nil nil nil])))
    (is (= nil
           (partition-inside true? false? []))))
  (testing "Start no stop"
    (is (= [[true nil nil] []]
           (partition-inside true? false? [nil true nil nil]))))
  (testing "Nested - these are weird..."
    (is (= [[true nil true] [true]]
           (partition-inside true? false? [nil true nil true])))
    (is (= [[true nil nil true nil false] [true nil false] []]
           (partition-inside true? false? [nil true nil nil true nil false nil false nil])))))

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
             "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"] []]
           (extract-lines real-log))))
  (testing "No traceback"
    (is (= [[]]
           (extract-lines (map str (range 10))))))
  (testing "Successful Retry"
    (is (= []
           (filter unsuccessful-retry?
                   (butlast
                     (extract-lines ["Traceback (most recent call last):"
                                     "  File \"./send_status.py\", line 227, in <module>"
                                     "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                                     ""
                                     "Attempt 1 failed! Trying again in 4 seconds..."
                                     "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                                     "[skipping] Running report"]))))))
  (testing "Unsuccessful Retry"
    (is (= [["Traceback (most recent call last):"
                                     "  File \"./send_status.py\", line 227, in <module>"
                                     "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                                     ""
                                     "Attempt 1 failed and there are no more attempts left!"
                                     "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"]]
           (filter unsuccessful-retry?
                   (butlast
                     (extract-lines ["Traceback (most recent call last):"
                                     "  File \"./send_status.py\", line 227, in <module>"
                                     "smtplib.SMTPDataError: (454, 'Temporary service failure')"
                                     ""
                                     "Attempt 1 failed and there are no more attempts left!"
                                     "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
                                     "[skipping] Running report"])))))))

(deftest test-unsuccessful-retry
  (testing "Filter this out"
    (is (false? (unsuccessful-retry?
                  ["Traceback"
                   "Other stuff Here"
                   "Attempt 1 failed! Trying again in 4 seconds..."
                   ""]))))
  (testing "Don't filter this one"
    (is (true? (unsuccessful-retry?
                 ["Traceback"
                  "Other stuff here"
                  "Attempt 1 failed and there are no more attempts left!"])))))

(deftest test-traceback-type
  (testing "Extract traceback type"
    (is (= "traceback.Thing: "
           (traceback-type ["1" "2" "3" "traceback.Thing: "]))))
  (testing "No traceback found"
    (is (= nil
           (traceback-type ["1" "2"])))))

(deftest test-subject
  (testing "Building traceback subject"
    (is (= "Subject - traceback.Thing: "
           (subject "Subject" ["1" "2" "3" "traceback.Thing: "]))))
  (testing "No traceback found"
    (is (= "Subject - "
           (subject "Subject" ["1" "2"])))))
