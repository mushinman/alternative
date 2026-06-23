(ns social.mushin.alternative.web.routes.api
  (:require [social.mushin.alternative.web.auth-utils :refer [failed-auth! check-basic-auth! remember-me-cookie]]
            [social.mushin.alternative.db.authentication :as authn]
            [clojure.tools.logging :as log]
            [social.mushin.alternative.uri :refer [join]]
            [clojure.string :as cstr]
            [social.mushin.alternative.uri :refer [uri]]
            [social.mushin.alternative.application.custom :as custom]
            [social.mushin.alternative.db.custom :as db-custom]
            [social.mushin.alternative.web.middleware.exception :as exception]
            [social.mushin.alternative.web.middleware.formats :as formats]
            [social.mushin.alternative.web.middleware.decode :as decode]
            [social.mushin.alternative.web.middleware.auth :as auth] 
            [social.mushin.alternative.utils :as utils]
            [social.mushin.alternative.web.utils :refer [create-restful-controller] :as web-utils]
            [social.mushin.alternative.application.users :as app-users]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.application.depot :as depot]
            [ring.util.http-response :refer [bad-request! created accepted ok unauthorized no-content
                                             not-found service-unavailable unauthorized!]]
            [java-time.api :as time]
            [social.mushin.alternative.system :as sys]
            [social.mushin.alternative.db.types :as types]
            [integrant.core :as ig]
            [reitit.coercion.malli :as malli]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.logger :as ring-logger]
            [social.mushin.alternative.web.middleware.tx-func :as tx]
            [reitit.swagger :as swagger]))

(def route-data
  {:coercion   malli/coercion
   :muuntaja   formats/instance
   :swagger    {:id ::api}
   :middleware [;; query-params & form-params
                ring-logger/wrap-log-response
                ;; Logging
                parameters/parameters-middleware
                ;; content-negotiation
                muuntaja/format-negotiate-middleware
                ;; Async headers
                tx/wrap-add-tx-fn
                ;; encoding response body
                muuntaja/format-response-middleware
                ;; exception handling
                coercion/coerce-exceptions-middleware
                ;; decoding request body
                muuntaja/format-request-middleware
                ;; coercing response bodys
                coercion/coerce-response-middleware
                ;; Decode special params
                decode/wrap-encoded-params
                ;; coercing request parameters
                coercion/coerce-request-middleware
                ;; exception handling
                exception/wrap-exception]})
;; Routes
(defn api-routes [opts]
  [["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title "social.mushin.alternative API"}}
           :handler (swagger/create-swagger-handler)}}]
   ["/v1"
    ["/are-you-ok"
     {:get {:handler (fn [_]
                       (ok
                        {:time (time/zoned-date-time (time/instant) "UTC")
                         :up-since (sys/process-start-time)
                         :threads (sys/thread-count)
                         :memory (sys/memory-usage)
                         :status :up}))}}] ; TODO no-cache response header

    ["/authn"
     ["/recall"
      {:post {:handler
              (create-restful-controller
               (fn [{:keys [depot]}
                    {:keys [cookies session]}]
                 (if-let [cookie-value (get-in cookies ["remember-me" :value])]
                   (if-let [selector-validator (cstr/split cookie-value #":")]
                     (if (= (count selector-validator) 2)
                       (if-let [cookie
                                (depot/recall-session depot (first selector-validator) (second selector-validator) {})]
                         ;; Recall-user destroyed their last token, so we must allocate them a new one.
                         ;;
                         (let [{:keys [user-id]} cookie
                               valid-during (time/duration 30 :days)
                               {:keys [selector validator doc]}
                               (authn/remember-user user-id valid-during)]
                           (log/info "Allocating new session" {:event :user-logged-in :user-id user-id :method :remember-me})
            
                           (depot/insert-session depot doc {})
                           (->  (ok {:message "Logged in"})
                                (assoc :session (assoc session :user-id user-id))
                                (remember-me-cookie selector validator valid-during)))
                         (unauthorized! {:error :invalid-remember-me :message "Your remember me cookie is invalid"}))
                       (unauthorized! {:error :invalid-remember-me :message "Your remember me cookie is invalid"}))
                     (unauthorized! {:error :invalid-remember-me :message "Your remember me cookie is invalid"}))
                   (unauthorized! {:error :no-remember-me :message "Your request was missing a remember me cookie"})))
               opts)}}]
     ["/let-me-in"
      {:post {:handler
              (create-restful-controller
               (fn [{:keys [depot]}
                    {:keys [headers session]}]
                 (when-not (get headers "authorization")
                   (failed-auth! {:error "missing authorization"
                                  :message "please authenticate using one of our supported schemas"}))

                 (let [[auth-type auth-arg] (cstr/split (get headers "authorization") #"\s+")
                       user-id (if (utils/icase-comp auth-type "Basic")
                                 (check-basic-auth! depot auth-arg)
                                 (bad-request! {:error "invalid_request" :message "Malformed authorization header"}))
                       valid-during (time/duration 30 :days)
                       {:keys [doc selector validator]} (authn/remember-user user-id valid-during)]

                   (log/info "Successfully logged in user" {:event :logged-in :user-id user-id})
                   (depot/insert-session depot doc {})
                   (-> (ok {:message "Logged in"})
                       (assoc :session (assoc session :user-id user-id))
                       (remember-me-cookie selector validator valid-during))))
               opts)}}]
     ["/bye-bye"
      {:post {:handler
              (create-restful-controller
               (fn [{:keys [depot]}
                    {:keys [session cookies]}]
                 (log/info "Logging out user " {:event :logged-out :user-id (:user-id session)})
                 (when-let [cookie-value (get-in cookies ["remember-me" :value])]
                   ;; Delete any remember-me cookies that were submitted.
                   (when-let [selector-validator (cstr/split cookie-value #":")]
                     (when (= (count selector-validator) 2)
                       (depot/delete-session depot (first selector-validator) (second selector-validator) {}))))
                 (-> (ok {:message "Logged out"})
                     (assoc :session (dissoc session :user-id))
                     (remember-me-cookie)))
               opts)}}]]

    ["/i"
     {:middleware [(partial auth/wrap-authenticate-user opts)]}
     ["/who-am-i"
      {:get {:handler
             (create-restful-controller
              (fn [{:keys [depot]}
                   {{:keys [user-id]} :session}]
                (if-let [user-doc (app-users/get-user-by-id depot user-id user-id {})]
                  (ok user-doc)
                  (not-found {:user-id user-id})))
              opts)}}]

     ["/rel"
      ["/follow"
       ["/s"
        {:get {:handler (create-restful-controller
                         (fn [{:keys [depot]}
                              {{:keys [user-id]} :session {{:keys [label category]} :path} :parameters}]
                           )
                         opts)}}]
       ["/u/:id"
        {:get {:handler (create-restful-controller
                         (fn [{:keys [depot]}
                              {{:keys [user-id]} :session {{:keys [label category]} :path} :parameters}]
                           )
                         opts)}
         :delete {:handler (create-restful-controller
                            (fn [{:keys [depot]}
                                 {{:keys [user-id]} :session {{:keys [label category]} :path} :parameters}]
                              )
                            opts)}
         :put {:handler (create-restful-controller
                         (fn [{:keys [depot]}
                              {{:keys [user-id]} :session {{:keys [label category]} :path} :parameters}]
                           )
                         opts)}}]]]

     ["/custom-data/:label"
      ["/d/:category"
       (let [path-params-schema
             [:map
              [:label db-custom/label-string-schema]
              [:category db-custom/label-string-schema]]]
         {:get {:handler (create-restful-controller
                          (fn [{:keys [db]}
                               {{:keys [user-id]} :session {{:keys [label category]} :path} :parameters}]
                            (if-let [result (custom/get-custom depot {} user-id user-id label category)]
                              (ok result)
                              (not-found {:label label :category category})))
                          opts)
                :parameters {:path path-params-schema}}
          :put {:handler (create-restful-controller
                          (fn [{:keys [db executor]}
                               {{:keys [user-id]} :session {:keys [body] {:keys [label category]} :path} :parameters
                                :keys [mushin/async?]}]
                            (custom/upsert-custom depot {} user-id user-id label category body)
                            (ok {}))
                          opts)
                :parameters {:body [:string]
                             :path path-params-schema}}
          :delete {:handler (create-restful-controller
                             (fn [{:keys [depot]}
                                  {{:keys [user-id]} :session {{:keys [label category]} :path} :parameters}]
                               (custom/delete-custom depot {} user-id user-id label category)
                               (no-content))
                             opts)
                   :parameters {:path path-params-schema}}})]]

     ["/delete-me"
      {:delete {:handler
                (create-restful-controller
                 (fn [{:keys [depot]}
                      {{:keys [user-id]} :session {{:keys [nickname password]} :body} :parameters}]
                   (let [credential-check-result (app-users/check-nickname-and-password depot nickname password)]
                     (if (uuid? credential-check-result)
                       (if (= credential-check-result user-id)
                         (do
                           (app-users/deactivate-user-by-id! depot user-id :user-self-delete)
                           (no-content))
                         (unauthorized {:resource-type :user
                                        :user-id user-id}))
                       (case credential-check-result
                         :no-account (not-found {:user-id user-id})
                         :wrong-nickname-or-password (unauthorized {:resource-type :user
                                                                    :user-id user-id})
                         :timeout (service-unavailable {})))))
                 opts)

                :parameters {:body [:map
                                    [:nickname :string]
                                    [:password :string]]}}}]

     ["/create-status"
      {:post {:handler
              (create-restful-controller
               (fn [{:keys [depot]}
                    {{:keys [user-id]} :session
                     {{:keys [content]} :body} :parameters}]
                 )
               opts)
              :parameters {:body [:map
                                  [:content :string]]}}}]]

    ["/users"
     {:middleware [(partial auth/wrap-authenticate-user opts true)]}
     ["/get-user/:id" {:get {:handler
                             (create-restful-controller
                              (fn [{:keys [depot]}
                                   {{{:keys [id]} :path} :parameters {:keys [user-id]} :session}]
                                (if-let [user (app-users/get-user-by-id depot user-id id {})]
                                  (ok user)
                                  (not-found {:user-id id})))
                              opts)}
                       :parameters {:path web-utils/id-schema}}]

     ["/create-user"
      {:post {:handler (create-restful-controller
                        (fn
                          [{{{:keys [host]} :url} :endpoint :keys [db bucket]}
                           {{{:keys [nickname password avatar banner bio display-name]
                              :or {bio ""
                                   display-name ""}} :body} :parameters}] 
                          (let [{{:keys [id]} :user}
                                (app-users/create-user! db bucket {} nickname password avatar banner bio display-name)]
                            (ok {:id id})))
                        opts)
              :parameters {:body [:map
                                  [:email {:optional true} types/email-schema]
                                  [:password [:string {:min 8 :max 128}]]
                                  [:avatar  {:description "mulitpart file" :optional true} :any]
                                  [:banner  {:description "mulitpart file" :optional true} :any]
                                  [:nickname users/nickname-schema]]}}}]]

    ["/admin"
     ["/custom-data/:id-or-nickname"
      ["/l"
       ["/:label"
        ["/d/:category"
         (let [path-params-schema
               [:map
                [:id-or-nickname [:or :uuid users/nickname-schema]]
                [:label db-custom/label-string-schema]
                [:category db-custom/label-string-schema]]]
           {:get {:handler (create-restful-controller
                            (fn [{:keys [depot]}
                                 {{:keys [user-id]} :session {{:keys [id-or-nickname label category]} :path} :parameters
                                  :keys [mushin/async?]}]
                              (if-let [result (custom/get-custom depot {:async? async?} id-or-nickname user-id label category)]
                                (ok result)
                                (not-found {:label label :category category})))
                            opts)
                  :parameters {:path path-params-schema}}
            :put {:handler (create-restful-controller
                            (fn [{{{:keys [host]} :url} :endpoint :keys [depot]}
                                 {{:keys [user-id]} :session {:keys [body] {:keys [id-or-nickname label category]} :path} :parameters
                                  :keys [mushin/async?]}]
                              (let [[_ category label]
                                    (custom/upsert-custom depot {:async? async?} id-or-nickname user-id label category body)
                                    resource-url (uri {:host host :path (str  "/api/v1/admin/custom-data/"
                                                                              id-or-nickname
                                                                              "/l"
                                                                              label
                                                                              "/d/"
                                                                              category)})]
                                (if async?
                                  (accepted {:message "Custom resource request accepted."
                                             :monitor-url resource-url})
                                  (ok {:resource-url resource-url}))))
                            opts)
                  :parameters {:body [:string]
                               :path path-params-schema}}
            :delete {:handler (create-restful-controller
                               (fn [{:keys [depot]}
                                    {{:keys [user-id]} :session {{:keys [id-or-nickname label category]} :path} :parameters
                                     :keys [mushin/async?]}]
                                 (custom/delete-custom depot {:async? async?} id-or-nickname user-id label category)
                                 (no-content))
                               opts)
                     :parameters {:path path-params-schema}}})]]]]]]])

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path "api"}
      :as   opts}]
  (fn [] [base-path route-data (api-routes opts)]))
