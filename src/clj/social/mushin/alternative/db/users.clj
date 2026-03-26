(ns social.mushin.alternative.db.users
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.util :as db-util]
            [clj-uuid :as uuid]
            [buddy.hashers :as hashers]
            [social.mushin.alternative.crypt.password :as crypt]
            [java-time.api :as jt]
            [malli.experimental.time :as mallt]
            [social.mushin.alternative.utils :refer [to-java-uri]]))

(def ^:private user-states-schema
  "Schema for user states.
  | Key          | State                     | Meaning                                |
  |:-------------|:--------------------------|:---------------------------------------|
  | `:ok`        | None                      | Account activated and in good standing |
  | `:timeout`   | Time the timeout expires. | Account is in timeout                  |
  | `:tombstone` | None                      | Account is dead/deactivated.           |
  "
  [:multi {:dispatch :type}
   [:ok [:map [:type :keyword]]]
   [:timeout [:map [:type :keyword] [:timeout (mallt/-zoned-date-time-schema)]]]  ; TODO implement timeout
   [:tombstone [:map [:type :keyword]]]])


(def user-schema
  "Schema for users.
  | Key                 | Type      | Meaning                                                           |
  |:--------------------|:----------|:------------------------------------------------------------------|
  | `xt/id`             | UUID      | Row key                                                           |
  | `email`             | string    | User email address                                                |
  | `log-counter`       | int       | How many times this user has logged in counted at most once daily |
  | `nickname`          | string    | The user's nickname                                               |
  | `password-hash`     | string    | Password hash                                                     |
  | `local`             | bool      | True if the user is local, false if foreign                       |
  | `bio`               | string    | The user's biography                                              |
  | `joined-at`         | Timestamp | The time the user created their account                           |
  | `last-logged-in-at` | Timestamp | The last the user logged in                                       |
  | `privacy-level`     | keyword   | Level of privacy. Can be `:open`, `:open-instance`, `:locked`     |
  "
  {::tiny-string  [:string {:min 1 :max 32}]
   ::short-string [:string {:min 1 :max 256}]
   ::long-string  [:string {:min 1 :max 4096}]

   :mushin.db/users
   [:map
    [:xt/id                   :uuid]
    [:email {:optional true}  [:and ::short-string [:re #".+@.+"]]]
    [:log-counter             :int]
    [:nickname                :string]
    [:display-name            :string]
    [:avatar {:optional true} 'uri?]
    [:banner {:optional true} 'uri?]
    [:password-hash           :string]
    [:bio                     :string]
    [:state                   user-states-schema]
    [:privacy-level           [:enum :open :open-instance :locked]]
    [:local?                  :boolean]
    [:joined-at               (mallt/-zoned-date-time-schema)]
    [:last-logged-in-at       (mallt/-zoned-date-time-schema)]]})

(defn can-login?
  "Check that a given password matches a given nickname.

  # Arguments
   - `xtdb-node`: Database.
   - `nickname`: User nickname.
   - `password`: The password for the `nickname`'s user account.

  # Return value
  The user's `:user-id` if a login is allowed,
  or a reason code for login rejection. Could be: `no-account`, `:timeout`,
  `:dead-account`, or `:wrong-nickname-or-password`"
  [xtdb-node nickname password]
  (let [{{:keys [type] :as state} :state :keys [password-hash xt/id]}
        (first (xt/q xtdb-node (xt/template (-> (from :mushin.db/users [{:nickname ~nickname} state password-hash xt/id])
                                                (limit 1)))))]
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

(defn check-nickname-and-password
  "Check that a given password matches a given nickname.
  Uses the same username/password verification as `can-login?`.

  # Arguments
   - `xtdb-node`: Database.
   - `nickname`: User nickname.
   - `password`: The password for the `nickname`'s user account.

  # Return value
  True if the user can login, false if not."
  [xtdb-node nickname password]
  (uuid? (can-login? xtdb-node nickname password)))
