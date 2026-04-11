(ns social.mushin.alternative.db.users
  (:require [malli.experimental.time :as mallt]
            [java-time.api :as time]
            [clj-uuid :as uuid]
            [social.mushin.alternative.utils :refer [grapheme-count]]
            [social.mushin.alternative.validators :refer [is-email-user-valid?]]
            [social.mushin.alternative.db.types :refer [uri-schema email-schema]]
            [social.mushin.alternative.crypt.password :as crypt]
            [social.mushin.alternative.files :refer [coerce-to-uri]]))

(defn- is-valid-nickname?
  "Return true if `v` is a valid nickname, otherwise false."
  [v]
  (and (string? v)
       (<= 1 (grapheme-count v) 32)
       (is-email-user-valid? v)))

(def nickname-schema
  "Malli schmea for nicknames."
  [:fn {:error/message "Must be valid email username, not empty, and under 32 characters"} is-valid-nickname?])

;; TODO we should probably move passweord hash into its own table since we probably want to erase it
;; when the user updates their profile.
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
    [:email {:optional true}  email-schema]
    [:log-counter             :int]
    [:nickname                nickname-schema]
    [:display-name            :string]
    [:avatar {:optional true} uri-schema]
    [:banner {:optional true} uri-schema]
    [:password-hash           :string]
    [:bio                     :string]
    [:state                   user-states-schema]
    [:privacy-level           [:enum :open :open-instance :locked]]
    [:local?                  :boolean]
    [:joined-at               (mallt/-zoned-date-time-schema)]
    [:last-logged-in-at       (mallt/-zoned-date-time-schema)]]})

(defn create-local-user
  [nickname password avatar-uri banner-uri bio display-name & email]
  (let [now (time/zoned-date-time)]
    (cond-> {:xt/id (uuid/v4)
             :nickname nickname
             :display-name display-name
             :local? true
             :state {:type :ok}
             :log-counter 0
             :avatar (coerce-to-uri avatar-uri)
             :banner (coerce-to-uri banner-uri)
             :bio bio
             :password-hash (crypt/hash-password password)
             :joined-at now
             :privacy-level :open
             :last-logged-in-at now}
      email (assoc :email email))))

;; TODO set avatar and banner URIs to some default.
(defn create-user-tombstone
  [user-id]
  {:xt/id user-id
   :display-name ""
   :state {:type :timeout}
   :bio ""
   :password-hash ""
   :privacy-level :open
   :last-logged-in-at (time/zoned-date-time)})
