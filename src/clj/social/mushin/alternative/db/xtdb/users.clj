(ns social.mushin.alternative.db.xtdb.users
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [assert-not-exists-tx delete-where erase-where]]
            [social.mushin.alternative.db.authentication :as authn]
            [social.mushin.alternative.db.users :as base-users]))

(defn can-login?
  "Check that a given password matches a given nickname.

  # Arguments
   - `db-con`: Database connection.
   - `nickname`: User nickname.
   - `password`: The password for the `nickname`'s user account.
   - `opts`: xtdb query options.

  # Return value
   The user's `:user-id` if a login is allowed,
   or a reason code for login rejection. Could be: `no-account`,
  `:dead-account`, or `:wrong-nickname-or-password`"
  ([db-con nickname password opts]
   (let [{{:keys [type] :as state} :state :keys [auth-entry user-id]}
         (first
          (xt/q db-con
                [(xt/template
                  (fn [nickname]
                    (-> (from :mushin.db/users [{:nickname nickname, :xt/id user-id} state])
                        (with {:auth-entry
                               (pull (fn [user-id]
                                       (-> (from :mushin.db/authn [* {:for-id user-id, :for-type :mushin.db/users}])
                                           (limit 1))))})
                        (limit 1))))
                 nickname]
                opts))]
     (cond
       (not= type :ok)
       (case type
         nil :no-account
         :timeout :timeout
         :tombstone :dead-account
         (throw (ex-info "Invalid state for account" {:invalid-state :user :state state})))

       (and auth-entry (authn/verify-password auth-entry password))
       user-id

       :else
       :wrong-nickname-or-password)))
  ([db-con nickname password]
   (can-login? db-con nickname password {})))

(defn user-exists?
  [db-con id-or-nickname opts]
  (->
   (xt/q db-con [(xt/template
                  (fn [id-or-nickname]
                    (-> (from :mushin.db/users [xt/id {~(if (string? id-or-nickname) :nickname :xt/id) id-or-nickname}])
                        (limit 1)))) 
                 id-or-nickname]
         opts)
   first
   boolean))


(defn deactivate-user-tx
  "Create a xtdb transaction part for deleting a user."
  [user-id]
  [;; Delete all the customs.
   (delete-where
    :mushin.db/custom
    (xt/template
     (fn [user-id]
       (from :mushin.db/custom [{:owner-id user-id :xt/id _id}])))
    user-id)
   ;; Delete all the authentications.
   (erase-where
    :mushin.db/authn
    (xt/template
     (fn [user-id]
       (from :mushin.db/authn [{:for-id user-id :for-type :mushin.db/users :xt/id _id}])))
    user-id)
   ;; TODO tombstone all their posts.
   [:patch-docs :mushin.db/users (base-users/create-user-tombstone user-id)]])

(defn get-user-id-by-nickname
  "Query the database for the user's id with `nickname`."
  [db-con nickname opts]
  (->
   (xt/q db-con [(xt/template
                  (fn [nickname]
                    (-> (from :mushin.db/users [:xt/id {:nickname nickname}])
                        (limit 1))))
                 nickname]
         opts)
   first
   :xt/id))

(defn get-user-display-data
  [db-con id-or-nickname opts]
  (first
   (xt/q db-con [(xt/template
                  (fn [id-or-nickname]
                    (-> (from :mushin.db/users [* {~(if (string? id-or-nickname) :nickname :xt/id) id-or-nickname}])
                        (limit 1)))) 
                 id-or-nickname]
         opts)))


(defn get-user-actor-data
  "Query the database for a user with `id-or-nickname`. If none exists, return `nil`.

  Also returns, merged with the user map, and each of the user's roles in the `:roles` key."
  [db-con id-or-nickname opts]
  (first
   (xt/q db-con [(xt/template
                  (fn [id-or-nickname]
                    (-> (from :mushin.db/users [{:xt/id user-id} {~(if (string? id-or-nickname) :nickname :xt/id) id-or-nickname}])
                        (with {:roles
                               (pull* (fn [user-id]
                                        (-> (unify
                                             (from :mushin.db/authorization-user-role [{:user-id user-id} role-id])
                                             (from :mushin.db/authorization-role [{:xt/id role-id} :name :attrs]))
                                            (without :xt/id))))})
                        (limit 1)))) 
                 id-or-nickname]
         opts)))

(defn get-user-with-actor
  "Query the database for a user with `id-or-nickname`. If none exists, return `nil`.

  Also returns, merged with the user map, and each of the user's roles in the `:roles` key."
  [db-con id-or-nickname opts]
  (first
   (xt/q db-con [(xt/template
                  (fn [id-or-nickname]
                    (-> (from :mushin.db/users [* {:xt/id user-id} {~(if (string? id-or-nickname) :nickname :xt/id) id-or-nickname}])
                        (with {:roles
                               (pull* (fn [user-id]
                                        (-> (unify
                                             (from :mushin.db/authorization-user-role [{:user-id user-id} role-id])
                                             (from :mushin.db/authorization-role [{:xt/id role-id} :name :attrs]))
                                            (without :xt/id))))})
                        (limit 1))))
                 id-or-nickname]
         opts)))

(defn insert-local-user-tx
  "Create an insertion transaction for `user`.

  This transaction will result in an exception if a user with the same nickname
  already exists."
  [{:keys [nickname xt/id] :as user} authn-entry]
  ;; TODO also assert that the ID doesn't exist.
  [(assert-not-exists-tx
    (xt/template (fn [nickname]
                   (-> (from :mushin.db/users [{:nickname nickname}])
                       (limit 1))))
    nickname)
   (assert-not-exists-tx
    (xt/template (fn [id]
                   (-> (from :mushin.db/users [{:xt/id id}])
                       (limit 1))))
    id)
   (assert-not-exists-tx
    (xt/template (fn [id]
                   (-> (from :mushin.db/authn [{:xt/id id}])
                       (limit 1))))
    (:xt/id authn-entry))
   [:put-docs :mushin.db/authn authn-entry]
   [:put-docs :mushin.db/users user]])

(defn search-user
  [db-con search-term opts]
  ;; TODO switch to like-regex when I figure out how that works.
  (xt/q db-con [(xt/template
                 (fn [search-term]
                   (-> (from :mushin.db/users [*])
                       (where (like nickname search-term)))))
                search-term]
        opts))
