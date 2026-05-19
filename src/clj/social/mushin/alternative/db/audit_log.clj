(ns social.mushin.alternative.db.audit-log
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))

(def audit-action-schema
  [:enum
   :audit/ban-user :audit/timeout-user])

(def audit-log-schema
  {:mushin.db/audit-log
   [:map
    [:xt/id         :uuid]
    [:actor-id      :uuid]
    [:action        audit-action-schema]
    [:context {:optional true}
     :any]
    ts/created-at]})

(defn create-audit-event
  ([actor-id action context]
   (cond-> {:xt/id (uuid/v4)
            :actor-id actor-id
            :action action
            :created-at (time/zoned-date-time)}
     context (assoc :context context)))
  ([actor-id action]
   (create-audit-event actor-id action nil)))
