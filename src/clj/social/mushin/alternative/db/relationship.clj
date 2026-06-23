(ns social.mushin.alternative.db.relationship
  (:require [social.mushin.alternative.db.types :refer [created-at]]
            [java-time.api :as time]))

(def ^:private relationship-type-schema [:type [:enum :follow :mute :block :react]])

(def ^:private common-rel-schema
  [:map
   [:id      :uuid]
   [:actor-id   :uuid]
   [:object-key :uuid]
   [:type       relationship-type-schema]
   created-at])

(def relationship-schema
  {:mushin.db/rel
   [:multi {:dispatch :type}
    [:follow
     common-rel-schema]
    [:mute
     common-rel-schema]
    [:block
     common-rel-schema]
    [:react
     common-rel-schema]]})

(defn create-follow-relationship
  [actor-id followee-id]
  {:id (random-uuid)
   :actor-id actor-id
   :created-at (time/zoned-date-time)
   :object-key followee-id
   :type :follow})

(defn create-mute-relationship
  [actor-id muted-id]
  {:id (random-uuid)
   :actor-id actor-id
   :type :mute
   :created-at (time/zoned-date-time)
   :object-key muted-id})


(defn create-block-relationship
  [actor-id blockee-id]
  {:id (random-uuid)
   :actor-id actor-id
   :type :block
   :created-at (time/zoned-date-time)
   :object-key blockee-id})

(defn create-react-relationship
  [actor-id reactee-id]
  {:id (random-uuid)
   :actor-id actor-id
   :type :react
   :created-at (time/zoned-date-time)
   :object-key reactee-id})
