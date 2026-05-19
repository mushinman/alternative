(ns social.mushin.alternative.cache
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [clojure.core.cache.wrapped :as cw]
            [kit.ig-utils :as ig-utils]))


(defmethod ig/init-key :social.mushin.alternative.cache/local-cache
  [_ cfg]
  (log/info "Creating a local cache with cfg" cfg)
  (cw/fifo-cache-factory {}))

(defmethod ig/resume-key :social.mushin.alternative.cache/local-cache
  [key opts old-opts old-impl]
  (log/info "Rusing cache")
  (ig-utils/resume-handler key opts old-opts old-impl))
