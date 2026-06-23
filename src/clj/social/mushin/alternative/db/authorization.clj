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
    [:id            :uuid]
    [:name             :string]
    [:attrs            [:set authorization-permissions-schema]]]})

(def authorization-user-role-schema
  {:mushin.db/user-roles
   [:map
    [:id            :uuid]
    [:user-id          :uuid]
    [:role-id          :uuid]
    ts/created-at]})

(def authorization-policy-schema
  {:mushin.db/role-policy
   [:map
    [:id         :uuid]
    [:role-id       :uuid]]})

(defn create-role
  [name attrs]
  {:id (uuid/v4)
   :name name
   :attrs attrs})

(defn create-user-role
  [user-id role-id]
  {:id (uuid/v4)
   :user-id user-id
   :role-id role-id
   :created-at (time/zoned-date-time)})
