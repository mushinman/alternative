(ns social.mushin.alternative.config
  (:require
   [kit.config :as config]
   [malli.registry :as mallr]
   [malli.core :as mallc]
   [malli.experimental.time :as malt]
   [social.mushin.alternative.db.users :as users]
   [social.mushin.alternative.db.remember-me :as remember-me]
   [social.mushin.alternative.db.statuses :as statuses]
   [social.mushin.alternative.db.resource-meta :as res-meta]
   [social.mushin.alternative.db.audit-log :as audit-log]
   [social.mushin.alternative.db.authorization :as authz]
   [social.mushin.alternative.db.actor :as actor]
   [social.mushin.alternative.db.authentication :as authn]
   [social.mushin.alternative.db.custom :as custom]))

(defonce schema-store (atom {}))

(defn register-schema!
  "Register schema specification `spec` to key `k`."
  [k spec]
  (swap! schema-store assoc k spec))

(def ^:const system-filename "system.edn")

(defn init-db-malli!
  "Adds database schemas to the malli registry."
  []
  (let [all-schemas (merge users/user-schema statuses/statuses-schema remember-me/remember-me
                           res-meta/resource-meta-schema audit-log/audit-log-schema
                           authz/authorization-role-schema authz/authorization-actor-role-schema
                           custom/custom-schema actor/actor-schema authn/authn-schema)]
    ;; Add our DB schemas.
    (mallr/set-default-registry!
     (mallr/composite-registry (mallc/default-schemas) (malt/schemas) all-schemas (mallr/mutable-registry schema-store)))))

(defn system-config
  [options]
  (init-db-malli!)
  (config/read-config system-filename options))
