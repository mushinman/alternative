(ns social.mushin.alternative.web.config
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.uri :refer [uri]]
            [social.mushin.alternative.utils :as ig-utils]))


(defmethod ig/init-key :social.mushin.alternative.web.config/endpoint [_ {:keys [url] :as config}]
  (log/info "Setting up web configuration" config)
  (uri url))

(defmethod ig/suspend-key! :social.mushin.alternative.web/endpoint [_ _])

(defmethod ig/resume-key :social.mushin.alternative.web/endpoint
  [key opts old-opts old-impl]
  (ig-utils/resume-handler key opts old-opts old-impl))
