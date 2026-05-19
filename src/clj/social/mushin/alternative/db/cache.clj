(ns social.mushin.alternative.db.cache
  (:require [clojure.core.cache.wrapped :as c]))

(defn lookup-or-miss*
  "Like lookup-or-miss but does not cache the value returned by `f`
  if it is `nil`."
  [cache key f]
  (if (c/has? cache key)
    (c/lookup cache key)
    (when-some [v (f)]
      (c/miss cache key v)
      v)))

(defn lookup-user-by-id-or-nickname
  "Lookup a user in the cache by its `id-or-nickname`.

  # Arguments
  - `cache`: An implementation of `clojure.core.cache.CacheProtocol`
  - `id-or-nickname`: A user's nickname or id.
  - `query-user-id-by-nickname`: A function that accepts a nickname and returns that user's id.
  - `query-user-by-id`: A function that accepts a user id and returns that user's document.

  # Return value
  The user with `id-or-nickname`, or `nil` if no such user exists."
  [cache id-or-nickname query-user-id-by-nickname query-user-by-id]
  (when-some [user-id
              (if (string? id-or-nickname)
                (lookup-or-miss* cache [:social.mushin.alternative/nickname id-or-nickname]
                                 (partial query-user-id-by-nickname id-or-nickname))
                id-or-nickname)]
    (lookup-or-miss* cache [:social.mushin.alternative/uid user-id]
                     (partial query-user-by-id user-id))))
