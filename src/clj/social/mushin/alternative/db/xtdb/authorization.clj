(ns social.mushin.alternative.db.xtdb.authorization
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [delete-where]]
            [social.mushin.alternative.db.authorization :as authr]))

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

