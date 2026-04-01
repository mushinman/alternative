(ns social.mushin.alternative.web.controllers.auth
  (:require [social.mushin.alternative.web.auth-utils :refer [failed-auth! check-basic-auth!]]
            [ring.util.http-response :refer [bad-request! ok unauthorized! conflict!]]
            [ring.util.response :as resp]
            [clojure.string :refer [join] :as cstr]
            [social.mushin.alternative.db.remember-me :as remember-me]
            [social.mushin.alternative.db.depot :as depot]
            [java-time.api :as time]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.db.types :as types]
            [social.mushin.alternative.web.utils :refer [wrap-db-errors]]
            [social.mushin.alternative.utils :as utils]))

(def create-user-body
  [:map
   [:email {:optional true} types/email-schema]
   [:password [:string {:min 8 :max 128}]]
   [:avatar  {:description "mulitpart file" :optional true} :any]
   [:banner  {:description "mulitpart file" :optional true} :any]
   [:nickname users/nickname-schema]])

(defn remember-me-cookie
  [response & [selector validator valid-for]]
  (let [value (if (and selector validator)
                (str selector ":" validator)
                "")
        max-age (if valid-for
                  (time/as valid-for :seconds)
                  0)]
    (resp/set-cookie response "remember-me" value
                     {:path "/api/session"
     ;;:domain "example.com" TODO
                      :http-only true
                      :secure true
                      :same-site :strict
                      :max-age max-age})))
(defn login! [{:keys [depot]}
              {:keys [headers session]}]
  (when-not (get headers "authorization")
    (failed-auth! {:error "missing authorization"
                   :message "please authenticate using one of our supported schemas"}))

  (let [[auth-type auth-arg] (cstr/split (get headers "authorization") #"\s+")
        user-id (if (utils/icase-comp auth-type "Basic")
                  (check-basic-auth! depot auth-arg)
                  (bad-request! {:error "invalid_request" :message "Malformed authorization header"}))
        {:keys [doc selector validator valid-for]} (remember-me/remember-user user-id)]

    (log/info "Successfully logged in user" {:event :logged-in :user-id user-id})
    (wrap-db-errors (depot/insert-session depot doc))
    (-> (ok {:message "Logged in"})
        (assoc :session (assoc session :user-id user-id))
        (remember-me-cookie selector validator valid-for))))

(defn logout! [{:keys [depot]}
               {:keys [session cookies]}]
  (log/info "Logging out user " {:event :logged-out :user-id (:user-id session)})
  (when-let [cookie-value (get-in cookies ["remember-me" :value])]
    ;; Delete any remember-me cookies that were submitted.
    (when-let [selector-validator (cstr/split cookie-value #":")]
      (when (= (count selector-validator) 2)
        (when-let [{:keys [xt/id]}
                   (wrap-db-errors (depot/recall-session depot (first selector-validator) (second selector-validator)))]
          (wrap-db-errors (depot/delete-session depot id))))))
  (-> (ok {:message "Logged out"})
      (assoc :session (dissoc session :user-id))
      (remember-me-cookie)))

(defn refresh-session!
  "Refreshes user session based on a `remember-me` cookie.

  Checks the validity of the incoming cookie. If valid: logs
  the user and creates a new session, and awards the user a
  new remember me cookie. If invalid: returns `unauthorized`.

  ## Arguments
  * `:xtdb-node` - xtdb
  * `:cookies` - HTTP request cookies

  ## Returns
  * HTTP 200 on success with new session and remember me cookies

  ## Throws
  * HTTP exception with code `unauthorized` on invalid cookie.
  "
  [{:keys [depot]}
   {:keys [cookies session]}]
  (if-let [cookie-value (get-in cookies ["remember-me" :value])]
    (if-let [selector-validator (cstr/split cookie-value #":")]
      (if (= (count selector-validator) 2)
        (if-let [cookie
                 (wrap-db-errors (depot/recall-session depot (first selector-validator) (second selector-validator)))]
          ;; Recall-user destroyed their last token, so we must allocate them a new one.
          ;;
          (let [{:keys [user-id xt/id]} cookie
                {:keys [selector validator doc valid-for]} (remember-me/remember-user user-id)]
            (log/info "Allocating new session" {:event :user-logged-in :user-id user-id :method :remember-me})
            
            (wrap-db-errors (depot/update-session depot doc id))
            (->  (ok {:message "Logged in"})
                 (assoc :session (assoc session :user-id user-id))
                 (remember-me-cookie selector validator valid-for)))
          (unauthorized! {:error :invalid-remember-me :message "Your remember me cookie is invalid"}))
        (unauthorized! {:error :invalid-remember-me :message "Your remember me cookie is invalid"}))
      (unauthorized! {:error :invalid-remember-me :message "Your remember me cookie is invalid"}))
    (unauthorized! {:error :no-remember-me :message "Your request was missing a remember me cookie"})))

(defn create-user
  "Create a new user."
  [{:keys [depot]}
   {{{:keys [nickname password avatar banner bio display-name]
      :or {bio ""
           display-name ""}} :body} :parameters
    :keys [mushin/async?]}]
  (let [avatar
        (if avatar
          (media/create-resource-from-static-image! (:tmpfile avatar)
                                        ;(if (mime/is-supported-image-type? ))
                                                    "image/png"
                                                    resource-map)
          (res/to-uri resource-map "default-avatar.png"))
        banner
        (if banner
          (media/create-resource-from-static-image! (:tmpfile banner)
                                                    "image/png"
                                                    resource-map)
          (res/to-uri resource-map "default-banner.png"))
        user-url (join endpoint (str "/@" nickname "/"))]
    (when (db-users/check-user-nickname-exists? xtdb-node nickname)
      (log/info {:event :creating-user-failed :nickname nickname :reason :user-already-exists})
      (conflict! {:error :user-already-exists :message "A user by that nickname already exists"}))
    (log/info {:event :creating-user :nickname nickname})
    (let [{:keys [xt/id] :as doc}
          (db-users/create-local-user nickname password
                                      user-url
                                      avatar banner
                                      bio display-name)]
      (if async?
        (do
          (db/submit-tx xtdb-node
                        [[:put-docs :mushin.db/users doc]])
          (created user-url {:id id}))
        (do
          (db/execute-tx xtdb-node
                         [[:put-docs :mushin.db/users doc]])
          (ok {:id id}))))))
