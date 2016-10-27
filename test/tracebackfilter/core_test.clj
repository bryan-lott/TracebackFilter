(ns tracebackfilter.core-test
  (:require [clojure.test :refer :all]
            [tracebackfilter.core :refer :all]))

(def real-log ["2016-10-21 12:15:34,191 - INFO - send_status.py - starting"
               "Traceback (most recent call last):"
               "  File \"./send_status.py\", line 227, in <module>"
               "    main(args, config)"
               "  File \"./send_status.py\", line 191, in main"
               "    send_email(config, html, args.recipients.split(','), chart_filename)"
               "  File \"./send_status.py\", line 176, in send_email"
               "    sender.send(message)"
               "  File \"/usr/lib/python2.7/dist-packages/mailer.py\", line 110, in send"
               "    self._send(server, msg)"
               "  File \"/usr/lib/python2.7/dist-packages/mailer.py\", line 140, in _send"
               "    server.sendmail(me, you, msg.as_string())"
               "  File \"/usr/lib/python2.7/smtplib.py\", line 746, in sendmail"
               "    raise SMTPDataError(code, resp)"
               "smtplib.SMTPDataError: (454, 'Temporary service failure')"
               ""
               "\n"
               "\r\n"
               "Attempt 1 failed! Trying again in 4 seconds..."
               "Fri Oct 21 12:15:40 UTC 2016 [skipping] report"
               "[skipping] Running report"
               "Running other tasks"])

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

(deftest test-traceback-start
  (testing "True"
    (is (true? (traceback-start? "Traceback"))))
  (testing "False"
    (is (false? (traceback-start? " Traceback")))
    (is (false? (traceback-start? "traceback")))
    (is (false? (traceback-start? "stuff and things")))))

(deftest test-traceback-end
  (testing "True"
    (is (true? (traceback-end? "didn't start with a space"))))
  (testing "False"
    (is (false? (traceback-end? " started with a space")))
    (is (false? (traceback-end? "psycopg2 is a tricky one to extract")))
    (is (false? (traceback-end? "Traceback's shouldn't be nested")))
    (is (false? (traceback-end? "DETAILs are important for context")))))

(deftest test-extract-traceback
  (testing "Traceback Exists"
    (is (= [["Traceback (most recent call last):"
             "  File \"./send_status.py\", line 227, in <module>"
             "    main(args, config)"
             "  File \"./send_status.py\", line 191, in main"
             "    send_email(config, html, args.recipients.split(','), chart_filename)"
             "  File \"./send_status.py\", line 176, in send_email"
             "    sender.send(message)"
             "  File \"/usr/lib/python2.7/dist-packages/mailer.py\", line 110, in send"
             "    self._send(server, msg)"
             "  File \"/usr/lib/python2.7/dist-packages/mailer.py\", line 140, in _send"
             "    server.sendmail(me, you, msg.as_string())"
             "  File \"/usr/lib/python2.7/smtplib.py\", line 746, in sendmail"
             "    raise SMTPDataError(code, resp)"
             "smtplib.SMTPDataError: (454, 'Temporary service failure')"] []]
           (extract-traceback real-log))))
  (testing "No traceback"
    (is (= [[]]
           (extract-traceback (map str (range 10)))))))