(ns social.mushin.alternative.db.users
  (:require [malli.experimental.time :as mallt]
            [java-time.api :as time]
            [clj-uuid :as uuid]
            [social.mushin.alternative.db.schema :as schema]
            [social.mushin.alternative.utils :refer [grapheme-count]]
            [social.mushin.alternative.validators :refer [is-email-user-valid?]]
            [social.mushin.alternative.errors :as err]
            [social.mushin.alternative.db.types :refer [uri-schema email-schema]]
            [social.mushin.alternative.uri :refer [uri]]))

(defn is-valid-nickname?
  "Return true if `v` is a valid nickname, otherwise false."
  [v]
  (and (string? v)
       (<= 1 (grapheme-count v) 32)
       (is-email-user-valid? v)))

(defmethod schema/doc-schema-check :social.mushin.alternative/users
  [[_ op docs]]
  (doseq [doc docs]
    (let [{:keys [meta/id meta/creator meta/owner nickname local? joined-at]} doc]
      (and
       (or (uuid? id)
           (throw (err/db-error "id is not a UUID" :schema-check {:id id})))
       (or (and (= :patch op) (nil? creator))
           (uuid? creator)
           (throw (err/db-error "creator is not a UUID" :schema-check {:creator creator
                                                                       :id id})))
       (or (and (= :patch op) (nil? owner))
           (uuid? owner)
           (throw (err/db-error "owner is not a UUID" :schema-check {:owner owner
                                                                     :id id})))
       (or (and (= :patch op) (nil? nickname))
           (is-valid-nickname? nickname)
           (throw (err/db-error "nickname is not a valid" :schema-check {:nickname nickname
                                                                         :id id})))
       (or (and (= :patch op) (nil? local?))
           (boolean? local?)
           (throw (err/db-error "local? is not a boolean" :schema-check {:local? local?
                                                                         :id id})))
       (or (and (= :patch op) (nil? joined-at))
           (schema/is-zdt? joined-at)
           (throw (err/db-error "joined-at is not a ZonedDateTime" :schema-check {:joined-at joined-at
                                                                                  :id id}))))))
  docs)


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
