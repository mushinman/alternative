(ns social.mushin.alternative.db.authorization
  "Authorization schema."
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))

(def authorization-permissions-schema
  [:enum
   :roles/read :roles/write :roles/delete
   :users/delete])

(def authorization-role-schema
  {:mushin.db/roles
   [:map
    [:xt/id            :uuid]
    [:name             :string]
    [:attrs            [:set authorization-permissions-schema]]]})

(def authorization-user-role-schema
  {:mushin.db/user-roles
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
  [user-id role-id]
  {:xt/id (uuid/v4)
   :user-id user-id
   :role-id role-id
   :created-at (time/zoned-date-time)})
