(ns social.mushin.alternative.db.tasks
  (:require [integrant.core :as ig]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.utils :as ig-utils]
            [social.mushin.alternative.application.depot :as depot]))

(defmethod ig/init-key :social.mushin.alternative.db.tasks/tasks [_ {:keys [depot]}]
  (log/info "Initializing db tasks...")
  (reify org.quartz.Job
    (execute [_this _]
      (log/info "Starting db tasks...")
      (log/info "Purging the db of expired session.")
      (depot/delete-expired-session depot {})
      (log/info "End db taskss."))))

(defmethod ig/suspend-key! :social.mushin.alternative.db.tasks/tasks [_ _]
  (log/info "Suspending db tasks"))

(defmethod ig/resume-key :social.mushin.alternative.db.tasks/tasks
  [key opts old-opts old-impl]
  (log/info "Resuming suspended db tasks")
  (ig-utils/resume-handler key opts old-opts old-impl))
