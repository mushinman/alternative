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
    [:id         :uuid]
    [:actor-id      :uuid]
    [:action        audit-action-schema]
    [:message {:optional true} :string]
    [:context {:optional true} :map]
    ts/created-at]})

(defn create-audit-event
  ([actor-id action context message]
   (cond-> {:id (uuid/v4)
            :actor-id actor-id
            :action action
            :created-at (time/zoned-date-time)}
     context (assoc :context context)
     message (assoc :message message)))
  ([actor-id action context-or-message]
   (let [[context message]
         (cond
           (nil? context-or-message) [nil nil]
           (string? context-or-message) [nil context-or-message]
           (map? context-or-message) [context-or-message nil])]
     (create-audit-event actor-id action context message)))
  ([actor-id action]
   (create-audit-event actor-id action nil nil)))
