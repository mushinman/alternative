(ns social.mushin.alternative.db.cache
  (:require [clojure.core.cache.wrapped :as c]
            [social.mushin.alternative.errors :refer [cache-miss-ex]])
  (:import [social.mushin.alternative.hosted.db AlternativeCacheMissException]))

(defprotocol CacheValueProvider
  "A provider for logical application state for placing into the cache."
  (get-user-id-from-nickname [p nickname]
    "Get a user's ID from its nickname, or `nil` if no such user exsts.")
  (get-user-actor-from-id [p user-id]
    "Get a user's actor/logical information from its user-id, or `nil` if it doesn't exist.")
  (get-role-from-id [p role-id]
    "Get logical role information from its `role-id`, or `nil` if no such role exists."))

(defn- default-wrapper-fn
  "Default wrapper function for caching."
  [value-fn key]
  (value-fn (second key)))

(defn- lookup-or-miss*
  "Wrapper round lookup-or-miss that uses its own default wrapper function. If the caller does not want to store anything in the
   cache then value-fn may throw AlternativeCacheMissException."
  ([cache key value-fn]
   (lookup-or-miss* cache key default-wrapper-fn value-fn))
  ([cache key wrap-fn value-fn]
   (try
     (c/lookup-or-miss cache key wrap-fn value-fn)
     (catch AlternativeCacheMissException _))))

(defn lookup-role-by-id
  "Lookup a role by its `role-id`.

  # Arguments
  - `cache`: An implementation of `clojure.core.cache.CacheProtocol`
  - `provider` An implementation of `CacheValue`
  - `role-id`: The role id to lookup

  # Return value
   The role with `role-id`, or `nil` if no such role exists."
  [cache provider role-id]
  (let [k [:social.mushin.alternative/role role-id]]
    (lookup-or-miss* cache
                     k
                     (fn []
                       (or (get-role-from-id provider role-id)
                           (cache-miss-ex k))))))

(defn lookup-user-by-id-or-nickname
  "Lookup a user in the cache by its `id-or-nickname`.

  # Arguments
  - `cache`: An implementation of `clojure.core.cache.CacheProtocol`
  - `provider` An implementation of `CacheValueProvider`
  - `id-or-nickname`: A user's nickname or id.

  # Return value
   The user with `id-or-nickname`, or `nil` if no such user exists."
  [cache provider id-or-nickname]
  (when-some [user-id
              (if (string? id-or-nickname)
                (let [k [:social.mushin.alternative/nickname id-or-nickname]]
                  (lookup-or-miss* cache k
                                   (fn [id-or-nickname]
                                     (or (get-user-id-from-nickname provider id-or-nickname)
                                         (cache-miss-ex k))))) 
                id-or-nickname)]
    (let [k [:social.mushin.alternative/uid user-id]]
      (when-let [{:keys [role-ids] :as user-doc}
                 (lookup-or-miss* cache 
                                  k
                                  (fn [user-id]
                                    (or (get-user-actor-from-id provider user-id)
                                        (cache-miss-ex k))))]
        (-> (dissoc user-doc :role-ids)
            (assoc :roles (into [] (map (partial lookup-role-by-id cache provider) role-ids))))))))
