(ns social.mushin.alternative.resources
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.resources.file-system-bucket :as file-bucket]
            [social.mushin.alternative.files :as files]
            [lambdaisland.uri :refer [uri]]
            [kit.ig-utils :as ig-utils]))

(defmethod ig/init-key :social.mushin.alternative.resources/provider [_ location]
  (log/info "Initializing the resource store provider...")
  (cond
    (contains? location :local)
    (let [{:keys [path bucket] :as config} (:local location)]
      (log/info "Creating filesystem resource provider with config:" config)
      (file-bucket/->FileSystemBucket (files/path path) (uri bucket)))

    :else (throw (ex-info "Unrecognized resource store provider settings" {:settings location}))))

(defmethod ig/suspend-key! :social.mushin.alternative.resources/provider  [_ _]
  (log/info "Suspending resource provider"))

(defmethod ig/resume-key :social.mushin.alternative.resources/provider
  [key opts old-opts old-impl]
  (log/info "Resuming suspended resource provider")
  (ig-utils/resume-handler key opts old-opts old-impl))
