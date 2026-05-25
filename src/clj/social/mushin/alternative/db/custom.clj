(ns social.mushin.alternative.db.custom
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [clj-uuid :as uuid]
            [java-time.api :as time]))

(def label-string-schema [:string {:min 1 :max 64}])

(def custom-schema
  {:mushin.db/custom
   [:map
    [:xt/id    :uuid]
    [:owner-id :uuid]
    [:label    label-string-schema]
    [:category label-string-schema]
    [:value    :string]
    ts/updated-at]})

(defn create-custom
  [owner-id label category value]
  (let [now (time/zoned-date-time)]
    {:xt/id (uuid/v4)
     :actor-id owner-id
     :label label
     :category category
     :value value
     :updated-at now}))
