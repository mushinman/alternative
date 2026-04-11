(ns social.mushin.alternative.db.remember-me
  (:require [malli.experimental.time :as mallt]
            [clj-uuid :as uuid]
            [java-time.api :as time]
            [social.mushin.alternative.digest :as digest]
            [social.mushin.alternative.codecs :as codecs]
            [social.mushin.alternative.tokens :as tokens]))

(def remember-me
  {:mushin.db/remember-me
   [:map {:closed true}
    [:xt/id                   :uuid]
    [:user                    :uuid]
    [:selector                :string]
    [:hashed-validator        :string]
    [:created-at              (mallt/-zoned-date-time-schema)]
    [:valid-for               :time/duration]]})

(defn remember-user
  ([user-id valid-for]
   (let [selector (codecs/bytes->b64u (tokens/generate-token 16))
         validator (tokens/generate-token 32)]
     {:selector selector
      :validator (codecs/bytes->b64u validator)
      :valid-for        valid-for
      :doc {:xt/id            (uuid/v4)
            :selector         selector
            :hashed-validator (digest/sha-256-b64u validator)
            :user             user-id
            :created-at       (time/zoned-date-time)
            :valid-for        valid-for}}))
  ([user-id] (remember-user user-id (time/duration 30 :days))))

