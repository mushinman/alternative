(ns social.mushin.alternative.db.xtdb.authorization
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [delete-where assert-not-exists-tx]]
            [social.mushin.alternative.db.authorization :as authr]))

(def ^:private query-role-by-name
  (xt/template
   (fn [role-name]
     (-> (from :mushin.db/roles [* {:name role-name}])
         (limit 1)))))

(defn- delete-user-role-tx
  "Create a transaction that removes a role from a user."
  [user-id role-id-or-name]
  (if (string? role-id-or-name)
    (delete-where
     (xt/template
      (fn [user-id role-name]
        (-> (unify
             (from :mushin.db/roles [{:name role-name :xt/id role-id}])
             (from :mushin.db/user-roles [{:role-id role-id :user-id user-id :xt/id id}]))
            (return {:_id id}))))
     user-id role-id-or-name)
    (delete-where
     :mushin.db/user-roles
     (xt/template
      (fn [user-id role-id]
        (from :mushin.db/user-roles [{:role-id role-id :user-id user-id :xt/id _id}])))
     user-id role-id-or-name)))

(defn- delete-role-tx
  "Create a transaction that deletes the role with `role-id-or-name` and all the `actor-roles` rows that reference it."
  [role-id-or-name]
  (if (string? role-id-or-name)
    [(delete-where
      :mushin.db/roles
      (xt/template
       (fn [role-name]
         (-> (from :mushin.db/roles [{:name role-name :xt/id _id}])
             (limit 1))))
      role-id-or-name)
     (delete-where
      :mushin.db/user-roles
      (xt/template
       (fn [role-name]
         (-> (unify
              (from :mushin.db/roles [{:name role-name :xt/id role-id}])
              (from :mushin.db/user-roles [{:role-id role-id :xt/id id}]))
             (return {:_id id}))))
      role-id-or-name)]

    [[:delete-docs :mushin.db/roles role-id-or-name]
     (delete-where
      :mushin.db/user-roles
      (xt/template
       (fn [role-id]
         (from :mushin.db/user-roles [{:role-id role-id :xt/id _id}])))
      role-id-or-name)]))

(defn upsert-role-tx
  [{:keys [name xt/id] :as role-doc}]
  [; Make sure we don't make duplicate roles.
   (assert-not-exists-tx
    (xt/template
     (fn [role-name new-role-id]
       (-> (form :mushin.db/roles [{:name role-name :xt/id doc-id}])
           (where (not= doc-id new-role-id)))))
    name id)
   [:patch-docs :mushin.db/roles role-doc]])

(defn get-logical-role-by-id
  "Return the logical rows for the role with `role-id`, or `nil` if no role exists."
  [db-con role-id opts]
  (first
   (xt/q db-con [(xt/template
                  (fn [role-id]
                    (-> (from :mushin.db/roles [name attrs {:xt/id role-id}])
                        (limit 1))))
                 role-id]
         opts)))

(defn get-role-by-id-or-name
  "Return the role identified by `role-id-or-name`, or `nil` if no such role exists."
  [db-con role-id-or-name opts]
  (first
   (xt/q db-con [(if (string? role-id-or-name)
                   query-role-by-name
                   (xt/template 
                    (fn [role-id]
                      (-> (from :mushin.db/roles [* {:xt/id role-id}])
                          (limit 1)))))
                 role-id-or-name]
         opts)))
