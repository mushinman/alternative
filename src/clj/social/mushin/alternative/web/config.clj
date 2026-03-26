(ns social.mushin.alternative.web.config
  (:require [integrant.core :as ig]
            [lambdaisland.uri :refer [uri]]
            [clojure.tools.logging :as log]
            [kit.ig-utils :as ig-utils]))


(defmethod ig/init-key :social.mushin.alternative.web.config/endpoint [_ config]
  (log/info "Setting up web configuration" config)
  (merge (uri "")
         config))

(defmethod ig/suspend-key! :social.mushin.alternative.web/endpoint [_ _])

(defmethod ig/resume-key :social.mushin.alternative.web/endpoint
  [key opts old-opts old-impl]
  (ig-utils/resume-handler key opts old-opts old-impl))
