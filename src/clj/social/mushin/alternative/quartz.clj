(ns social.mushin.alternative.quartz
  (:require
    [aero.core :as aero]
    [integrant.core :as ig]
    [social.mushin.alternative.utils :as ig-utils]
    [troy-west.cronut :as cronut]))

;; Copied from https://github.com/kit-clj/kit/blob/bf96b3e5c07e87862416a5990cbc8d480394f754/libs/kit-quartz/src/kit/edge/scheduling/quartz.clj

(defmethod aero/reader 'cronut/trigger
  [_ _ value]
  (cronut/trigger-builder value))

(defmethod aero/reader 'cronut/cron
  [_ _ value]
  (cronut/shortcut-cron value))

(defmethod aero/reader 'cronut/interval
  [_ _ value]
  (cronut/shortcut-interval value))

(defmethod ig/suspend-key! :cronut/scheduler [_ _])

(defmethod ig/resume-key :cronut/scheduler
  [key opts old-opts old-impl]
  (ig-utils/resume-handler key opts old-opts old-impl))

;; Means of setting environment properties during runtime
;; Handy in case there's a scenario where you can't (for whatever reason) set
;; secrets in your JVM properties
(defmethod ig/init-key :scheduling.quartz/env-properties
  [_ properties]
  (doseq [[k v] properties]
    (when (some? v)
      (System/setProperty (name k) v))))

