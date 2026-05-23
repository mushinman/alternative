(ns social.mushin.alternative.db.xtdb.resource-meta
  (:require [social.mushin.alternative.db.xtdb.util :as db-util]
            [xtdb.api :as xt]))

(defn insert-resource-tx
  "Create an insertion transaction for a `resource`.

  This transaction will result in an exception if a user with the same nickname
  already exists."
  [{:keys [xt/id] :as doc}]
  [(db-util/assert-not-exists-tx
     [(xt/template (fn [id]
                     (-> (from :mushin.db/resource-meta [{xt/id id}])
                         (limit 1))))
      id])
   [:put-docs :mushin.db/resource-meta doc]])

(defn get-resource-by-id
  "Execute a query for getting a resource id."
  [db-con id]
  (->
   (xt/q db-con [(xt/template
                  (fn [id]
                    (from :mushin.db/resource-meta [* {:xt/id id}])
                    (limit 1)))
                 id])
   first))

(defn resource-exists?
  "Execute a query that checks if a resource with `id` exists."
  [db-con id]
  (->
   (xt/q db-con [(xt/template
                  (fn [id]
                    (-> (from :mushin.db/resource-meta [{:xt/id id}])
                        (limit 1)
                        (return {:result true}))))
                 id])
   not-empty
   boolean))

(defn delete-resource-meta-tx
  "Create a transaction part that deletes a resource by its name."
  [name]
  [:delete-docs :mushin.db/resource-meta name])

