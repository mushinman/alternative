(ns social.mushin.alternative.web.utils
  (:require [ring.util.http-response :refer [unauthorized! conflict! service-unavailable! 
                                             internal-server-error! not-found!]]
            [social.mushin.alternative.errors :as err]
            [clojure.tools.logging :as log]))
            

(def id-schema
  "A schema for a map with just a key called `:id` and a `:uuid` value.
   Useful for path params."
  [:map [:id :uuid]])

(defn no-account-error
  [nickname-or-id]
  (err/wrap-throw "No account" {:code :no-account
                                :module :application
                                :nickname-or-id nickname-or-id}))

(defn unauthorized
  [nickname-or-id]
  (err/wrap-throw "The user is unauthorized to perform this action"
                  {:code :unauthorized
                   :module :application
                   :nickname-or-id nickname-or-id}))


(defn wrong-nickname-or-password
  [nickname]
  (err/wrap-throw "Wrong nickname or password" {:nickname nickname
                                                :module :application
                                                :code :wrong-username-password}))

(defn user-timeout
  [nickname]
  (err/wrap-throw "User is on a timeout" {:nickname nickname
                                          :module :application
                                          :code :user-timeout}))

;; TODO put more messages and such in here.
(defn internal-error->http-error
  "Convert a database exception to a HTTP response."
  [e]
  (log/error e "Internal error")
  (let [{:keys [type] :as error-map} (ex-data e)]
    (if (and error-map
             (= type :social.mushin.alternative.errors/error)) 
      ;; If it's an internal error, determine the cause and throw an HTTP error.
      (let [{:keys [module code]} error-map
            message (ex-message e)]
        (case module
          ;; Database errors.
          :db
          (case code
            :conflict
            (conflict!
             {:code :resource-already-exists
              :message message})

            :assert-failed
            (conflict! {:code :conflict
                        :message message})
            :forbidden
            (unauthorized! {:code :unauthorized
                            :message message})

            :unavailable
            (service-unavailable! {:code :unavailable
                                   :message message})

            (internal-server-error! {:code :internal-error
                                     :message message}))
          ;; Application-layer errors.
          :application
          (case code
            :no-account
            (not-found! {:message message
                         :code code})

            (:wrong-username-password :unauthorized)
            (unauthorized! {:code code
                            :message message})

            (internal-server-error! {:code code
                                     :message message}))))
      (internal-server-error! {:type :internal-error}))))

(defmacro def-api-controller
  "Wrap any exceptions thrown by `form`, converting them into a HTTP error."
  [name docstring args & body]
  `(defn ~name ~docstring ~args
     (try
       ~@body
       (catch Throwable e#
         (internal-error->http-error e#)))))
