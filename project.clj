(defproject tracebackfilter "0.1.0-SNAPSHOT"
  :description "extract multi-line log information and send to external"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"] ;; arg parsing
                 [amazonica "0.3.76"]]
  :target-path "target/%s"
  :main ^:skip-aot tracebackfilter.core
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Xms4g" "-Xmx4g" "-server"]}})


