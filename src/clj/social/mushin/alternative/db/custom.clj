(ns social.mushin.alternative.db.custom
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))


(def custom-schema
  {:mushin.db/custom
   [:map
    [:xt/id    :uuid]
    [:actor-id :uuid]
    [:label    [:string {:min 1 :max 64}]]
    [:category [:string {:min 1 :max 64}]]
    [:value    :string]
    ts/created-at
    ts/updated-at]})

(defn create-custom
  [actor-id label category value]
  (let [now (time/zoned-date-time)]
    {:xt/id (uuid/v4)
     :actor-id actor-id
     :label label
     :category category
     :value value
     :created-at now
     :updated-at now}))
