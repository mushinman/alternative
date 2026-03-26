(ns social.mushin.alternative.db.resource-meta
  (:require [social.mushin.alternative.db.timestamps :as ts]
            [social.mushin.alternative.utils :refer [to-java-uri]]
            [social.mushin.alternative.db.util :as db-util]
            [java-time.api :as time]
            [xtdb.api :as xt]))
            

(def resource-meta-schema
  {:mushin.db/resource-meta
   [:map {:closed true}
    [:xt/id                   :string]
    [:location                'uri?]
    [:mime-type               :string]
    ts/created-at]})

(defn create-resource-meta-doc
  [name location mime-type]
  {:xt/id name 
   :location (to-java-uri location)
   :mime-type mime-type
   :created-at (time/zoned-date-time)})

(defn insert-resource-tx
  [doc]
  (db-util/insert-unless-exists-tx
   :mushin.db/resource-meta
   doc
   [:location]))

(defn get-resource-by-id
  [con id]
  (->
   (xt/q con [(xt/template
               (fn [id]
                 (from :mushin.db/resource-meta [* {:xt/id id}])
                 (limit 1)))
              id])
   first))

(defn resource-exists?
  [db-con id]
  (db-util/record-exists? db-con :mushin.db/resource-meta id))

(defn delete-resource-meta-tx
  [name]
  [:delete-docs :mushin.db/resource-meta name])
