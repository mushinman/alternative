(ns social.mushin.alternative.application.executor
  (:import [java.util.concurrent ExecutorService Callable Future]))

(defn submit
  ^Future
  [^ExecutorService executor ^Callable fn]
  (.submit executor fn))

