(ns social.mushin.alternative.db.xtdb.users
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [assert-not-exists-tx]]
            [clj-uuid :as uuid]
            [honey.sql :as sql]
            [buddy.hashers :as hashers]
            [social.mushin.alternative.db.users :as base-users]
            [social.mushin.alternative.crypt.password :as crypt]
            [java-time.api :as jt]
            [social.mushin.alternative.files :refer [coerce-to-host-uri coerce-to-uri]]
            [social.mushin.alternative.utils :refer [to-java-uri]]))

(def ^:private safe-user-columns
  "A list of columns that are safe to return in the API (e.g. not the password)."
  '[xt/id log-counter nickname display-name avatar banner ap-id bio state privacy-level local? joined-at last-logged-in-at])

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
   (let [{{:keys [type] :as state} :state :keys [password-hash xt/id]}
         (first
          (xt/q db-con
                [(xt/template
                  (fn [nickname]
                    (-> (from :mushin.db/users [{:nickname nickname} state password-hash xt/id])
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

       (and password-hash (:valid (hashers/verify password password-hash)))
       id

       :else
       :wrong-nickname-or-password)))
  ([db-con nickname password]
   (can-login? db-con nickname password {})))

(defn user->xtdb-doc
  "Convert any values in `user` to equivalent values that xtdb will accept.

  This function is the reverse of `user->xtdb-doc`."
  [{:keys [avatar banner] :as user}]
  (assoc user
         ;; xtdb only likes native URIs.
         :avatar (coerce-to-host-uri avatar)
         :banner (coerce-to-host-uri banner)))

(defn xtdb-doc->user
  "Convert any values in `doc` from their xtdb equivalent values to their internal values.

  This function is the reverse of `user->xtdb-doc`."
  [{:keys [avatar banner] :as doc}]
  (assoc doc 
         :avatar (coerce-to-uri avatar)
         :banner (coerce-to-uri banner)))

(defn deactivate-user-tx
  "Create a xtdb transaction part for deleting a user."
  [user-id]
  [:patch-docs :mushin.db/users (base-users/create-user-tombstone user-id)])

(defn get-user-by-nickname
  "Query the database for a user with `nickname`. If none exists, return `nil`.

  This query returns only the safe columns from the user table."
  [db-con nickname opts]
  (first (xt/q db-con [(xt/template
                        (fn [nickname]
                          (-> (from :mushin.db/users [~@safe-user-columns {:nickname nickname}])
                              (limit 1))))
                       nickname]
               opts)))


(defn get-user-by-id
  "Query the database for a user with `id`. If none exists, return `nil`.

   This query returns only the safe columns from the user table."
  [db-con id opts]
  (first (xt/q db-con [(xt/template
                        (fn [id]
                          (-> (from :mushin.db/users [~@safe-user-columns {:xt/id id}])
                              (limit 1))))
                       id]
               opts)))

(defn insert-user-tx
  "Create an insertion transaction for `user`.

  This transaction will result in an exception if a user with the same nickname
  already exists."
  [{:keys [nickname] :as user}]
  ;; TODO also assert that the ID doesn't exist.
  [(assert-not-exists-tx
    (xt/template (fn [nickname]
                   (-> (from :mushin.db/users [{:nickname nickname}])
                       (limit 1))))
    nickname)
   [:put-docs :mushin.db/users user]])

(defn search-user
  [db-con search-term opts]
  ;; TODO switch to like-regex when I figure out how that works.
  (xt/q db-con [(xt/template
                 (fn [search-term]
                   (-> (from :mushin.db/users [~@safe-user-columns])
                       (where (like nickname search-term)))))
                search-term]
        opts))

