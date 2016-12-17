(defproject tracebackfilter "0.1.0-SNAPSHOT"
  :description "extract multi-line log information and send to external"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"] ;; logging
                 [org.clojure/tools.cli "0.3.5"] ;; arg parsing
                 [amazonica "0.3.76" :exclusions [com.amaonaws/aws-java-sdk
                                                  com.amazonaws/amazon-kinesis-client]]  ;; talk to aws, exclude everything
                 [com.amazonaws/aws-java-sdk-core "1.10.49"] ;; include aws core
                 [com.amazonaws/aws-java-sdk-sns "1.10.49"] ;; include sns
                 [clj-time "0.13.0"]] ;; timestamps
                                                                                    
  :target-path "target/%s"
  :main ^:skip-aot tracebackfilter.core
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Xms4g" "-Xmx4g" "-server"]}})


