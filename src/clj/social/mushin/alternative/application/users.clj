(ns social.mushin.alternative.application.users
  (:require [social.mushin.alternative.application.depot :as depot]
            [social.mushin.alternative.resources.bucket :as bucket]
            [lambdaisland.uri :as lu]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.errors :as err]))

(defn get-user-by-id
  "Get a user by its `user-id`, or `nil` if no such user exists."
  ([depot user-id depot-opts]
   (depot/get-user-by-id depot user-id depot-opts))
  ([depot user-id]
   (get-user-by-id depot user-id nil)))

(defn deactivate-user-by-id!
  ([depot user-id depot-opts]
   (depot/deactivate-user depot user-id depot-opts))
  ([depot user-id]
   (deactivate-user-by-id! depot user-id nil)))

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
          (lu/uri "http://unknown"))
        banner
        (if banner
          (bucket/create-resource-from-static-image! (:tmpfile banner)
                                                     "image/png"
                                                     bucket)
          ;; TODO better handle default images.
          (lu/uri "http://unknown"))]
    (when (depot/get-user-by-nickname depot nickname)
      (log/info {:event :creating-user-failed :nickname nickname :reason :user-already-exists})
      (err/app-error "A user by that nickname already exists" :user-already-exists {}))
    (log/info {:event :creating-user :nickname nickname})

    (let [doc (users/create-local-user nickname password
                                       avatar banner
                                       bio display-name)]
      {:user doc
       :db-result (depot/insert-user depot doc depot-opts)})))
