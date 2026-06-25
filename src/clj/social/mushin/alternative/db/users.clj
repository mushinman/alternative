(ns social.mushin.alternative.db.users
  (:require [malli.experimental.time :as mallt]
            [java-time.api :as time]
            [clj-uuid :as uuid]
            [social.mushin.alternative.utils :refer [grapheme-count]]
            [social.mushin.alternative.validators :refer [is-email-user-valid?]]
            [social.mushin.alternative.db.types :refer [uri-schema email-schema]]
            [social.mushin.alternative.uri :refer [uri]]))

(defn- is-valid-nickname?
  "Return true if `v` is a valid nickname, otherwise false."
  [v]
  (and (string? v)
       (<= 1 (grapheme-count v) 32)
       (is-email-user-valid? v)))

(def nickname-schema
  "Malli schmea for nicknames."
  [:fn {:error/message "Must be valid email username, not empty, and under 32 characters"} is-valid-nickname?])

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
  "Schema for users."
  {::tiny-string  [:string {:min 1 :max 32}]
   ::short-string [:string {:min 1 :max 256}]
   ::long-string  [:string {:min 1 :max 4096}]

   :social.mushin.alternative/user-documents
   [:map
    [:actor-id                :uuid]
    [:nickname                nickname-schema]
    [:display-name            :string]
    [:avatar {:optional true} uri-schema]
    [:banner {:optional true} uri-schema]
    [:bio                     :string]
    [:privacy-level           [:enum :open :open-instance :locked]]
    [:local?                  :boolean]
    [:joined-at               (mallt/-zoned-date-time-schema)]]})

(defn create-local-user
  ([actor-id nickname avatar-uri banner-uri bio display-name email]
   (let [now (time/zoned-date-time)]
     (cond-> {:nickname nickname
              :display-name display-name
              :local? true
              :state {:type :ok}
              :avatar (uri avatar-uri)
              :banner (uri banner-uri)
              :bio bio
              :joined-at now
              :privacy-level :open}
       email (assoc :email email))))
  ([nickname avatar-uri banner-uri bio display-name]
   (create-local-user nickname avatar-uri banner-uri bio display-name nil)))

;; TODO set avatar and banner URIs to some default.
(defn create-user-tombstone
  [user-id]
  {:id user-id
   :display-name ""
   :state {:type :tombstone}
   :bio ""
   :privacy-level :open
   :last-logged-in-at (time/zoned-date-time)})
