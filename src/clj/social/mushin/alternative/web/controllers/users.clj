(ns social.mushin.alternative.web.controllers.users
  (:require [social.mushin.alternative.web.utils :refer [def-api-controller] :as web-utils]
            [social.mushin.alternative.db.depot :as depot]
            [social.mushin.alternative.resources.bucket :as bucket]
            [lambdaisland.uri :as lu]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.db.types :as types]
            [ring.util.http-response :refer [bad-request! created ok unauthorized! conflict! no-content]]))

(def create-user-body
  [:map
   [:email {:optional true} types/email-schema]
   [:password [:string {:min 8 :max 128}]]
   [:avatar  {:description "mulitpart file" :optional true} :any]
   [:banner  {:description "mulitpart file" :optional true} :any]
   [:nickname users/nickname-schema]])

(def user-id-schema
  [:map
   [:id :uuid]])

(def get-user-schema
  [:map
   [:nickname :string]])

(def-api-controller
 get-user
 "Get a user."
 [{:keys [depot]}
  {{{:keys [id]} :path} :parameters}]
 (log/info "Got user" id)
 (ok (depot/get-user-by-id depot id)))

(def-api-controller
 create-user
 "Create a new user."
 [{{{:keys [host]} :url} :endpoint :keys [depot bucket]}
  {{{:keys [nickname password avatar banner bio display-name]
     :or {bio ""
          display-name ""}} :body} :parameters
   :keys [mushin/async?]}]
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
         (lu/uri "http://unknown"))
       user-url (lu/join host (str "/@" nickname "/"))]
   (when (depot/get-user-by-nickname depot nickname)
     (log/info {:event :creating-user-failed :nickname nickname :reason :user-already-exists})
     (conflict! {:error :user-already-exists :message "A user by that nickname already exists"}))
   (log/info {:event :creating-user :nickname nickname})

   (let [{:keys [xt/id] :as doc}
         (users/create-local-user nickname password
                                  avatar banner
                                  bio display-name)]
     (if async?
       (do
         (depot/insert-user depot doc {:async? true})
         (created user-url {:id id}))
       (do
         (depot/insert-user depot doc)
         (ok {:id id}))))))
