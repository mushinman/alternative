(ns social.mushin.alternative.application.users
  (:require [social.mushin.alternative.application.depot :as depot]
            [social.mushin.alternative.resources.bucket :as bucket]
            [social.mushin.alternative.uri :refer [uri]]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.db.custom :as custom]
            [social.mushin.alternative.db.authentication :as authn]
            [social.mushin.alternative.errors :as err]))

(defn get-user-by-id
  "Get a user by its `user-id`, or `nil` if no such user exists.

   If `actor-id-optional` is non-nil: check if the actor can see the user with `user-id`."
  [depot actor-id-optional user-id depot-opts]
  (when (and actor-id-optional (not ))
    (throw (err/app-error "" :unauthorized {:actor-id actor-id-optional :user-id user-id})))
  (depot/get-by-nickname-or-id depot user-id :full depot-opts))

(defn deactivate-user-by-id!
  ([depot user-id deactivation-method depot-opts]
   (log/info {:event :deactivating-user :user-id user-id :reason deactivation-method})
   (depot/compose-and-transact-txs depot depot-opts (depot/deactivate-user depot user-id depot-opts)))
  ([depot user-id deactivation-method]
   (deactivate-user-by-id! depot user-id deactivation-method nil)))

(defn check-nickname-and-password
  "Check a user's `nickname` and `password` for validity.
  Returns true if the `password` is correct for `nickname`, otherwise false.

  See `Depot` for further explanation."
  ([d nickname password opts] (depot/check-nickname-and-password d nickname password opts))
  ([d nickname password] (check-nickname-and-password d nickname password {})))

(defn create-user!
  "Create a user. Returns a map of the following result:
  `{:user doc, :db-result database-return-value}`."
  [depot bucket depot-opts nickname password avatar banner bio display-name]
  (let [avatar
        (if avatar
          (bucket/create-resource-from-static-image! (:tmpfile avatar)
                                        ;(if (mime/is-supported-image-type? ))
                                                     "image/png"
                                                     bucket)
          (uri "http://unknown"))
        banner
        (if banner
          (bucket/create-resource-from-static-image! (:tmpfile banner)
                                                     "image/png"
                                                     bucket)
          ;; TODO better handle default images.
          (uri "http://unknown"))]
    (when (depot/user-exists? depot nickname depot-opts)
      (log/info {:event :creating-user-failed :nickname nickname :reason :user-already-exists})
      (err/app-error "A user by that nickname already exists" :user-already-exists {}))
    (log/info {:event :creating-user :nickname nickname})
    (let [{:keys [xt/id] :as user-doc} (users/create-local-user nickname 
                                                                avatar banner
                                                                bio display-name)
          auth-entry (authn/create-password-hashed-authn-entry password id :mushin.db/users)]
      {:user user-doc
       :db-result (depot/compose-and-transact-txs
                   depot
                   depot-opts
                   (depot/insert-local-user depot user-doc auth-entry depot-opts))})))

