(ns social.mushin.alternative.web.controllers.health
  (:require [java-time.api :as time]
            [social.mushin.alternative.system :as sys]
            [ring.util.http-response :refer [ok]]))

(defn check
  [_]
  (ok
   {:time (time/zoned-date-time (time/instant) "UTC")
    :up-since (sys/process-start-time)
    :threads (sys/thread-count)
    :memory (sys/memory-usage)
    :status :up}))
