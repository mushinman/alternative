(ns social.mushin.alternative.db.resource-meta
  (:require [social.mushin.alternative.db.types :as types]
            [social.mushin.alternative.utils :refer [to-java-uri]]
            [java-time.api :as time]))
            

(def resource-meta-schema
  {:mushin.db/resource-meta
   [:map {:closed true}
    [:xt/id                   :string]
    [:location                'uri?]
    [:mime-type               :string]
    types/created-at]})

(defn create-resource-meta-doc
  [name location mime-type]
  {:xt/id name 
   :location (to-java-uri location)
   :mime-type mime-type
   :created-at (time/zoned-date-time)})

