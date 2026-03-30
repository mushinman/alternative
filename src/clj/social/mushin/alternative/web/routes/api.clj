(ns social.mushin.alternative.web.routes.api
  (:require
   [social.mushin.alternative.web.controllers.health :as health]
   [social.mushin.alternative.web.controllers.auth :as auth-handlers]
   [social.mushin.alternative.web.middleware.exception :as exception]
   [social.mushin.alternative.web.middleware.formats :as formats]
   [social.mushin.alternative.web.middleware.decode :as decode]
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
     {:get {:handler health/check}}] ; TODO no-cache response header

    ["/authn"
     ["/recall"
      {:post {:handler (partial auth-handlers/refresh-session! opts)}}]
     ["/let-me-in"
      {:post {:handler (partial auth-handlers/login! opts)}}]
     ["/bye-bye"
      {:post {:handler (partial auth-handlers/logout! opts)}}]]

    #_["/i"
     {:middleware [(partial auth/wrap-authenticate-user opts)]}
     ["/who-am-i"
      {:get {:handler (partial self/get-user opts)}}]

     ;; TODO authorization middleware that checks the arguments and denies based off
     ["/delete-me"
      {:delete {:handler (partial self/delete-self! opts)
                :parameters {:body self/delete-me-body-schema}}}]

     ["/create-status"
      {:post {:handler (partial self/create-status! opts)
              :parameters {:body self/statuses-body-schema}}}]


     ["/add-media"
      {:post {:handler (partial self/add-media! opts)}}]]
 
    ;; ["/statuses/timeline/:nickname"
    ;;  {:get  {:handler (partial statuses/get-timeline opts)
    ;;          :middleware [(partial auth/wrap-authenticate-user opts)]
    ;;          :parameters {:query statuses/get-timeline-query}}}]
                                        ;["/create-picture" {:handler (partial statuses/create-picture-post! opts)
                                        ;                    :middleware [(partial auth/wrap-authenticate-user opts)]
                                        ;                    :parameters {:body statuses/create-picture-post-body}}]

    ;; ["/create-status-post" {:handler (partial statuses/create-status-post! opts)
    ;;                         :middleware [(partial auth/wrap-authenticate-user opts)]
    ;;                         :parameters {:body statuses/create-status-body}}]

    ;; ["/statuses/s/:id" {:get  {:handler (partial statuses/get-status opts)
    ;;                            :middleware [(partial auth/wrap-authenticate-user opts)]
    ;;                            :parameters {:query statuses/get-status-query
    ;;                                         :path statuses/status-query}}
    ;;                     :delete {:handler (partial statuses/delete-status! opts)
    ;;                              :middleware [(partial auth/wrap-authenticate-user opts)]
    ;;                              :parameters {:query statuses/get-status-query
    ;;                                           :path statuses/status-query}}}]
    ]])

(derive :reitit.routes/api :reitit/routes)

(defmethod ig/init-key :reitit.routes/api
  [_ {:keys [base-path]
      :or   {base-path "api"}
      :as   opts}]
  (fn [] [base-path route-data (api-routes opts)]))
