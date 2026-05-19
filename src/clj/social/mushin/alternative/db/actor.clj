(ns social.mushin.alternative.db.actor
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))

(def actor-schema
  {:mushin.db/actor
   [:map
    [:xt/id      :uuid]
    [:actor-type :keyword]
    ts/created-at]})

(defn create-actor
  [actor-type]
  {:xt/id (uuid/v4)
   :created-at (time/zoned-date-time)
   :actor-type actor-type})
