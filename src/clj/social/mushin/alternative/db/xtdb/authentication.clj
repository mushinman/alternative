(ns social.mushin.alternative.db.xtdb.authentication
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.digest :as digest]
            [honey.sql :as sql]
            [social.mushin.alternative.db.xtdb.util :as db-util :refer [erase-where]]
            [social.mushin.alternative.codecs :as codecs]
            [honey.sql.helpers :as h]))

(def purge-invalid-tokens-query-tx
  "xtdb transaction part to purge all invalid remember-me tokens."
  [:sql (sql/format
         (-> (h/erase-from :mushin.db/authn)
             (h/where
              [:exists
               [:xtql (xt/template
                       (-> (from :mushin.db/authn [payload created-at])
                           (where (and
                                   (= (. payload type) :remember-me)
                                   (> (current-timestamp) (+ (. payload valid-during) created-at))))))]])))])


(def forget-everybody-tx
  "xtdb transaction part to purge all tokens."
  [:sql (sql/format
         (-> (h/erase-from :mushin.db/authn)
             (h/where
              [:exists
               [:xtql (xt/template
                       (-> (from :mushin.db/authn [payload created-at])
                           (where (and
                                   (= (. payload type) :remember-me)
                                   (> (current-timestamp) (+ (. payload valid-during) created-at))))))]])))])


(defn create-insert-session-tx
  "Create a xtdb transaction for inserting a session doc."
  [{:keys [for-id for-type] {:keys [type]} :payload :as doc}]
  [(case type
     :password-hash
     ;; Delete currently existing password.
     (erase-where
      :mushin.db/authn
      (xt/template
       (fn [for-id for-type]
         (-> (from :mushin.db/authn [{:for-id for-id :for-type for-type} payload])
             (where (= (. payload type) :password-hash))
             (limit 1))))
      for-id for-type)

     :remember-me
     ;; Delete any remember-me token(s) that would conflict with the new token.
     ;; TODO only delete the token for the client-id.
     (erase-where
      :mushin.db/authn
      (xt/template
       (fn [for-id for-type]
         (-> (from :mushin.db/authn [{:for-id for-id :for-type for-type} payload])
             (where (= (. payload type) :remember-me)))))
      for-id for-type))
   [:put-docs :mushin.db/authn doc]])

(defn recall-user
  "Query the database for a remember-me session for the given `validator` and `selector`.

  On success: returns the `:created-at`, `:valid-during`, `:for-id`, and `:for-type` columns in a map.
  On failure: `nil`."
  [db-con selector validator return-id opts]
  (when-let
      [{:keys [hashed-validator] :as token}
       (first
        (xt/q
         db-con
         [(xt/template
           (fn [q-selector]
             (-> (from :mushin.db/authn [created-at valid-during for-id for-type hashed-validator xt/id
                                         {:payload.type :remember-me} payload])
                 (where (and
                         (= (. payload selector) q-selector)
                         (< (current-timestamp)
                            (+ created-at valid-during))))
                 (limit 1))))
          selector]
         opts))]
    (when (digest/eq (codecs/b64u->bytes hashed-validator) (digest/sha-256-bytes (codecs/b64u->bytes validator)))
      (cond-> (dissoc token :hashed-validator)
        (not return-id) (dissoc token :xt/id)))))
