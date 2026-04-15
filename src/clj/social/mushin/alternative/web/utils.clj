(ns social.mushin.alternative.web.utils
  (:require [ring.util.http-response :refer [unauthorized conflict service-unavailable 
                                             internal-server-error not-found]]
            [social.mushin.alternative.errors :as err]
            [clojure.tools.logging :as log])
  (:import [social.mushin.alternative.hosted.application AlternativeApplicationException]
           [social.mushin.alternative.hosted.db AlternativeDatabaseException]))
            

(def id-schema
  "A schema for a map with just a key called `:id` and a `:uuid` value.
   Useful for path params."
  [:map [:id :uuid]])


(defn create-restful-controller
  "Create a controller for the HTTP REST api.  Automatically wraps all internal exception types.

  `f` Can be function of one argument, which is the HTTP context map.
   Or, `f` can be a function of two arguments: An optional argument and then the HTTP context map."
  ([f opts]
   (fn [ctx]
     (try
       (if opts
         (f opts ctx)
         (f ctx))
       (catch AlternativeApplicationException e
         (log/error e "Application logic failure in REST controller")
         (let [code (err/ex-code e)
               message (ex-message e)]
           (case code
             :no-account
             (not-found {:message message
                         :code code})

             (:wrong-username-password :unauthorized)
             (unauthorized {:code code
                            :message message})

             (internal-server-error {:code code
                                     :message message}))))
       (catch AlternativeDatabaseException e
         (log/error e "Database failure in REST controller")
         (let [code (err/ex-code e)
               message (ex-message e)]
           (case code
             :conflict
             (conflict
              {:code :resource-already-exists
               :message message})

             :assert-failed
             (conflict {:code :conflict
                        :message message})
             :forbidden
             (unauthorized {:code :unauthorized
                            :message message})

             :unavailable
             (service-unavailable {:code :unavailable
                                   :message message})

             (internal-server-error {:code :internal-error
                                     :message message})))))))
  ([f]
   (create-restful-controller f nil)))
