(ns tracebackfilter.logwriter
  (:import (java.io RandomAccessFile))
  (:require [clojure.string :refer [join starts-with? split]]
            [clojure.java.io :refer [writer]]
            [clj-time.core :as -time]))


(def log
  "2017-03-30 22:26:30,522 - INFO - fill_address_hash.py - starting
2017-03-30 22:26:30,524 - INFO - file_subscriber.py - starting
2017-03-30 22:26:30,524 - INFO - order_source_identifier.py - starting
2017-03-30 22:26:30,534 - INFO - import_country.py - starting
2017-03-30 22:26:30,538 - INFO - import_subscriber_apikey.py - starting
2017-03-30 22:26:38,216 - INFO - file_subscriber.py - MinId 5689206616; MaxId 5689206615
2017-03-30 22:26:38,260 - WARNING - aws.py - No data was written to file /tmp/tmp4DlgjR for table file_subscriber.  Skipping upload to s3 and copy to Redshift.
2017-03-30 22:26:39,302 - INFO - file_subscriber.py - all done.
2017-03-30 22:26:39,316 [74866.140092506212160] (tasks.py:185) INFO Exit code 0 from task ./file_subscriber.py --incremental --blocksize 50000000 {DEBUG_ARG}
2017-03-30 22:27:20,247 - INFO - fill_address_hash.py - No records to load to Redshift
2017-03-30 22:27:20,949 - INFO - fill_address_hash.py - all done.
2017-03-30 22:27:20,963 [74864.140092506212160] (tasks.py:185) INFO Exit code 0 from task ./fill_address_hash.py --threads 1 {DEBUG_ARG}
2017-03-30 22:27:29,412 [71762.140092362446592] (connectionpool.py:238) INFO Resetting dropped connection: logs.us-east-1.amazonaws.com
2017-03-30 22:27:29,456 [71762.140092362446592] (connectionpool.py:238) INFO Resetting dropped connection: logs.us-east-1.amazonaws.com
2017-03-30 22:27:30,331 - WARNING - aws.py - No data was written to file /tmp/tmpIUMlvL for table order_source_identifier.  Skipping upload to s3 and copy to Redshift.
2017-03-30 22:27:31,236 - INFO - order_source_identifier.py - all done.
2017-03-30 22:27:31,249 [74867.140092506212160] (tasks.py:185) INFO Exit code 0 from task ./order_source_identifier.py {DEBUG_ARG}
2017-03-30 22:27:31,250 [74867.140092506212160] (tasks.py:160) INFO Executing task ./order_subscriber.py {DEBUG_ARG} (attempt: 1/5)
2017-03-30 22:27:31,250 [74867.140092506212160] (tasks.py:168) INFO Interpolated command ./order_subscriber.py
2017-03-30 22:27:31,395 - INFO - order_subscriber.py - starting
2017-03-30 22:29:43,920 - WARNING - aws.py - No data was written to file /tmp/tmpEWc9Pl for table order_subscriber.  Skipping upload to s3 and copy to Redshift.
2017-03-30 22:29:51,429 - INFO - order_subscriber.py - No rows to insert into order_subscriber for partner boxer
2017-03-30 22:29:51,431 - WARNING - aws.py - No data was written to file /tmp/tmpdgaY3B for table order_subscriber.  Skipping upload to s3 and copy to Redshift.
2017-03-30 22:30:01,733 - INFO - connectionpool.py - Starting new HTTP connection (1): 127.0.0.1
2017-03-30 22:30:01,736 - INFO - connectionpool.py - Starting new HTTP connection (1): 127.0.0.1
2017-03-30 22:30:01,823 - INFO - connectionpool.py - Starting new HTTPS connection (1): tmp.s3.amazonaws.com
2017-03-30 22:30:04,608 - INFO - connectionpool.py - Starting new HTTPS connection (1): tmp.s3.amazonaws.com
2017-03-30 22:30:11,485 - INFO - order_subscriber.py - No rows to insert into order_subscriber for partner xxxxxxxx
2017-03-30 22:30:11,485 - WARNING - aws.py - No data was written to file /tmp/tmpe8IvlD for table order_subscriber.  Skipping upload to s3 and copy to Redshift.
Traceback (most recent call last):
  File \"./report_panel_source.py\", line 404, in <module>
    main(args, files)
  File \"./report_panel_source.py\", line 349, in main
    panel_source_report(args, files)
  File \"./report_panel_source.py\", line 341, in panel_source_report
    hive.refresh_partition(\"table-name\", partition, input_database=in_db, output_database=out_db)
  File \"/home/ecxrm/.local/lib/python2.7/site-packages/ci_aws/hiveq.py\", line 489, in refresh_partition
    job_options=[\"dynamic-partition\"] if is_dynamic else None)
  File \"/home/ecxrm/.local/lib/python2.7/site-packages/ci_aws/hiveq.py\", line 203, in run_query
    \"Hive command failed: {stderr}\".format(stderr=hc.get_log())
Exception: Hive command failed: Running 2 queries in a single run.
set, add, delete and create temporary query commands that are part of this query wont be added to the session.
2017-03-31 14:24:20,813 INFO  hivecli.py:161 - __init__ - Default hive version of the account is set as 1.2
2017-03-31 14:24:20,852 INFO  hivecli.py:731 - getQHSHostName - Using Hive tier.
mdhist20170331-23141-1l5yh9j 100% ||||||||||||||||||| Time: 00:00:00   0.00 B/s
qexec20170331-23141-1vpvj3w 100% |||||||||||||||||||| Time: 00:00:00   0.00 B/s
2017-03-31 14:24:22,435 INFO  hivecli.py:456 - getStandaloneCmd - Using hive version 1.2 for hadoop2 cluster
log4j:WARN No such property [rollingPolicy] in org.apache.log4j.RollingFileAppender.
17/03/31 14:24:24 INFO conf.HiveConf: HiveConf of name hive.io.override.jsonserde does not exist
17/03/31 14:24:24 INFO conf.HiveConf: HiveConf of name hive.io.file.cache.basedir does not exist
")


(defn repeat-log
  [n-lines]
  (take n-lines (cycle (split log #"\n"))))


(defn main
  "Spit random logs into a file for testing.
  Example: (main 10 \"test.log\")"
  [n-lines filename]
  (with-open [w (writer filename :append false)]
    (doseq [line (repeat-log n-lines)]
      (Thread/sleep 1000)
      (.write w (str line \newline))
      (.flush w))))
