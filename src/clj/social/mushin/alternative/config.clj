(ns social.mushin.alternative.config
  (:require
   [malli.registry :as mallr]
   [aero.core :as aero]
   [clojure.java.io :as io]
   [malli.core :as mallc]
   [malli.experimental.time :as malt]
   [social.mushin.alternative.db.users :as users]
   [social.mushin.alternative.db.statuses :as statuses]
   [social.mushin.alternative.db.resource-meta :as res-meta]
   [social.mushin.alternative.db.custom :as custom]
   [social.mushin.alternative.db.audit-log :as audit-log]
   [integrant.core :as ig]
   [social.mushin.alternative.db.authorization :as authz]
   [social.mushin.alternative.db.authentication :as authn]))


;; These ig functions come from https://github.com/kit-clj/kit/blob/bf96b3e5c07e87862416a5990cbc8d480394f754/libs/kit-core/src/kit/config.clj#L8
(defmethod aero/reader 'ig/ref
  [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'ig/refset
  [_ _ value]
  (ig/refset value))

(defmethod ig/init-key :system/env [_ env] env)

(defonce schema-store (atom {}))

(defn register-schema!
  "Register schema specification `spec` to key `k`."
  [k spec]
  (swap! schema-store assoc k spec))

(def ^:const system-filename "system.edn")

(defn init-db-malli!
  "Adds database schemas to the malli registry."
  []
  (let [all-schemas (merge users/user-schema statuses/statuses-schema
                           res-meta/resource-meta-schema audit-log/audit-log-schema
                           authz/authorization-role-schema authz/authorization-user-role-schema
                           custom/custom-schema authn/authn-schema)]
    ;; Add our DB schemas.
    (mallr/set-default-registry!
     (mallr/composite-registry (mallc/default-schemas) (malt/schemas) all-schemas (mallr/mutable-registry schema-store)))))

(defn system-config
  [options]
  (init-db-malli!)
  (if-let [resource (io/resource system-filename)]
    (aero/read-config resource options)
    (throw (ex-info "" {}))))
