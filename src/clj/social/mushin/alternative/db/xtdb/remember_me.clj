(ns social.mushin.alternative.db.xtdb.remember-me
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.digest :as digest]
            [honey.sql :as sql]
            [social.mushin.alternative.db.xtdb.util :as db-util]
            [social.mushin.alternative.codecs :as codecs]))

(def purge-invalid-tokens-query-tx
  "xtdb transaction part to purge all invalid tokens."
  [:sql (-> {:erase-from [:mushin.db/remember-me]
             :where [:> [:+ :created-at :valid-for] [:now]]}
            sql/format
            first)])


(def forget-everybody-tx
  "xtdb transaction part to purge all tokens."
  [:sql (first (sql/format {:erase-from [:mushin.db/remember-me]}))])

(defn erase-session-tx
  "Create a xtdb transaction part for erasing a session."
  [session-id]
  [:erase-docs :mushin.db/remember-me session-id])

(defn update-session-tx
  "Create a xtdb transaction for erasing an old session doc and inserting a new session doc."
  [doc old-session-id]
  [(erase-session-tx old-session-id)
   [:put-docs :mushin.db/remember-me doc]])

(defn create-insert-session-tx
  "Create a xtdb transaction for inserting a session doc."
  [{:keys [xt/id] :as doc}]
  (update-session-tx doc id))

;; TODO test this.
(defn get-token-value
  "Get the value of a token from the token string.

  ## Arguments
  * `db-con` - Database connection.
  * `token` - Token string

  ## Returns
  * `nil` if there is no token matching `token` in the database.
  * An empty map if there is a token matching `token` in the database, but there is no value associated with it.
  * The value, which is always a map, if there is a matching token in the database and if there is a value associated with it.
  "
  [db-con token]
  (let [value (first (xt/q db-con
                           (xt/template
                            [(fn [token]
                              (-> (from :mushin.db/remember-me [created-at valid-for value {:token token}])
                                (where (< (current-timestamp)
                                          (+ created-at valid-for)))
                                (limit 1)
                                (return value)))
                             token])))]
    (when value
      (or (:value value) value))))



(defn recall-user
  ([db-con selector validator opts]
   (when-let
       [token
        (first
         (xt/q
          db-con
          (xt/template [(fn []
                          (-> (from :mushin.db/remember-me [created-at valid-for user hashed-validator xt/id {:selector selector}])
                              (where (< (current-timestamp)
                                        (+ created-at valid-for)))
                              (limit 1)
                              (return hashed-validator user xt/id)))
                        selector])
          opts))]
     (when (digest/eq (codecs/b64u->bytes (:hashed-validator token)) (digest/sha-256-bytes (codecs/b64u->bytes validator)))
       (dissoc token :hashed-validator))))
  ([db-con selector validator]
   (recall-user db-con selector validator {})))
