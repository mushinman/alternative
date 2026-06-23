(ns social.mushin.alternative.db.authentication
  (:require [social.mushin.alternative.crypt.password :as crypt]
            [social.mushin.alternative.db.timestamps :as ts]
            [buddy.hashers :as hashers]
            [malli.experimental.time :as mallt]
            [java-time.api :as time]
            [clj-uuid :as uuid]
            [social.mushin.alternative.digest :as digest]
            [social.mushin.alternative.codecs :as codecs]
            [social.mushin.alternative.tokens :as tokens]))

(def ^:private authn-types [:enum :password-hash :remember-me])

(def authn-schema
  {:mushin.db/authn
   [:multi {:dispatch :type}
    [:password-hash
     [:map
      [:type authn-types]
      [:hash :string]]]
    [:remember-me
     [:map
      [:type authn-types]
      [:valid-during     (mallt/-duration-schema)]
      [:selector         :string]
      [:hashed-validator :string]]]]})

(defn create-password-hashed-authn-entry
  [password for-id for-type]
  (let [now (time/zoned-date-time)]
    {:id (uuid/v4)
     :for-id for-id
     :for-type for-type
     :payload {:hash (crypt/hash-password password)
               :type :password-hash}
     :created-at now}))

(defn verify-password
  [{{:keys [hash]} :payload} password]
  (:valid (hashers/verify password hash)))

(defn remember-user
  [user-id valid-during]
  (let [selector (codecs/bytes->b64u (tokens/generate-token 16))
        validator (tokens/generate-token 32)
        now (time/zoned-date-time)]
    {:doc {:id (uuid/v4)
           :for-id user-id
           :for-type :mushin.db/user-documents
           :created-at now
           :payload {:type :remember-me
                     :selector selector
                     :hashed-validator (digest/sha-256-b64u validator)
                     :valid-during valid-during}}
     :selector selector
     :validator (codecs/bytes->b64u validator)}))
