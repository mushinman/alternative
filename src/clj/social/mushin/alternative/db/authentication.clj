(ns social.mushin.alternative.db.authentication
  (:require [social.mushin.alternative.crypt.password :as crypt]
            [social.mushin.alternative.db.timestamps :as ts]
            [buddy.hashers :as hashers]
            [java-time.api :as time]
            [clj-uuid :as uuid]))

(def ^:private authn-types [:enum :password-hash])

(def authn-schema
  {:mushin.db/authn
   [:map
    [:xt/id            :uuid]
    ts/created-at
    ts/updated-at
    [:payload
     [:multi {:dispatch :type}
      [:password-hash
       [:map
        [:type authn-types]
        [:hash :string]]]]]]})

(defn create-password-hashed-authn-entry
  [password]
  (let [now (time/zoned-date-time)]
    {:xt/id (uuid/v4)
     :payload {:hash (crypt/hash-password password)
               :type :password-hash}
     :created-at now
     :updated-at now}))

(defn verify-password
  [{:keys [hash]} password]
  (:valid (hashers/verify password hash)))
