(ns social.mushin.alternative.db.authorization
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))

(def authorization-permissions-schema
  [:enum
   :admin-panel/read ; Enable logging into the admin panel.
   :roles/read :roles/write :roles/delete
   :users/delete])

(def authorization-role-schema
  {:mushin.db/authorization-role
   [:map
    [:xt/id            :uuid]
    [:name             :string]
    [:attrs            [:set authorization-permissions-schema]]]})

(def authorization-actor-role-schema
  {:mushin.db/authorization-actor
   [:map
    [:xt/id            :uuid]
    [:actor-id         :uuid]
    [:role-id          :uuid]
    ts/created-at]})

(defn create-role
  [name attrs]
  {:xt/id (uuid/v4)
   :name name
   :attrs attrs})

(defn create-actor-role
  [actor-id role-id]
  {:xt/id (uuid/v4)
   :actor-id actor-id
   :role-id role-id
   :created-at (time/zoned-date-time)})
