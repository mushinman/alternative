(ns social.mushin.alternative.db.authorization
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))

(def authorization-permissions-schema
  [:enum
   :roles/read :roles/write :roles/delete
   :users/delete])

(def authorization-role-schema
  {:mushin.db/authorization-role
   [:map
    [:xt/id            :uuid]
    [:name             :string]
    [:attrs            [:set authorization-permissions-schema]]]})

(def authorization-user-role-schema
  {:mushin.db/authorization-actor
   [:map
    [:xt/id            :uuid]
    [:user-id          :uuid]
    [:role-id          :uuid]
    ts/created-at]})

(defn create-role
  [name attrs]
  {:xt/id (uuid/v4)
   :name name
   :attrs attrs})

(defn create-user-role
  [actor-id role-id]
  {:xt/id (uuid/v4)
   :user-id actor-id
   :role-id role-id
   :created-at (time/zoned-date-time)})
