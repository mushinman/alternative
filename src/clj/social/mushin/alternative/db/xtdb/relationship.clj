(ns social.mushin.alternative.db.xtdb.relationship
  (:require [social.mushin.alternative.db.xtdb.util :refer [delete-where assert-exists-tx assert-not-exists-tx]]
            [honey.sql :as sql]
            [xtdb.api :as xt]))

(defn- ensure-user-is-valid-object-tx
  [object-user-id]
  (assert-exists-tx
   (xt/template
    (fn [object-id]
      (-> (from :mushin.db/users [state {:xt/id object-id}])
          (where (not= (. state type) :tombstone))
          (limit 1))))
   object-user-id))

(defn delete-rel-tx
  ([rel-id]
   [:delete-docs :mushin.db/rel rel-id])
  ([actor-id object-id type]
   [(delete-where
     :mushin.db/rel
     (xt/template
      (fn [actor-id object-id type]
        (-> (from :mushin.db/rel [{:actor-id actor-id, :type type, :object-key object-id :xt/id _id}])
            (limit 1))))
     actor-id object-id type)]))

(defn insert-rel-tx
  [{:keys [actor-id object-key type] :as rel}]
  (-> (case type
        ;; Ensure valid db state:

        :follow
        [(ensure-user-is-valid-object-tx object-key)
         ; Ensure that the actor isn't blocked by the account they're trying to follow.
         (assert-not-exists-tx
          (xt/template
           (fn [actor-id object-id]
             (-> (from :mushin.db/rel [{:actor-id object-id, :object-key actor-id, :type :block}])
                 (limit 1))))
          actor-id object-key)]

        :block
        [(ensure-user-is-valid-object-tx object-key)
         ;; If the now blocked user follows the actor: unfollow them.
         (delete-where
          :mushin.db/rel
          (xt/template
           (fn [actor-id object-id]
             (-> (from :mushin.db/rel [{:actor-id object-id, :object-key actor-id, :type :follow :xt/id _id}])
                 (limit 1))))
          actor-id object-key)]

        ;; Else: nothing.
        [])
      (into (delete-rel-tx actor-id object-key type))
      (into [; Finally, insert the document.
             [:put-docs :mushin.db/rel rel]])))


(defn get-relationships-for-actor
  [db-con actor-id rel-types opts]
  (xt/q
   db-con
   [(xt/template
     (fn [actor-id rel-types]
       (-> (unify
            (unnest {type rel-types})
            (from :mushin.db/rel [* {:actor-id actor-id, :type type}])))))
    actor-id rel-types]
   opts))


(defn get-relationships-for-object
  [db-con object-key rel-types opts]
  (xt/q
   db-con
   [(xt/template
     (fn [object-key rel-types]
       (-> (unify
            (unnest {type rel-types})
            (from :mushin.db/rel [* {:object-key object-key, :type type}])))))
    object-key rel-types]
   opts))

(defn get-relationship-between
  ([db-con actor-id object-key rel-type opts]
   (first
    (xt/q
     db-con
     [(xt/template
       (fn [actor-id object-key rel-type]
         (-> (from :mushin.db/rel [* {:object-key object-key, :actor-id actor-id, :type rel-type}])
             (limit 1))))
      actor-id object-key rel-type]
     opts))))
