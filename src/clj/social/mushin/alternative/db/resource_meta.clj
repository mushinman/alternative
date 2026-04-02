(ns social.mushin.alternative.db.resource-meta
  (:require [social.mushin.alternative.db.types :as types]
            [lambdaisland.uri :refer [uri]]
            [java-time.api :as time]))
            

(def resource-meta-schema
  {:mushin.db/resource-meta
   [:map {:closed true}
    [:xt/id                   :string]
    [:location                types/uri-schema]
    [:mime-type               :string]
    types/created-at]})

(defn create-resource-meta-doc
  [name location mime-type]
  {:xt/id name 
   :location (uri location)
   :mime-type mime-type
   :created-at (time/zoned-date-time)})

