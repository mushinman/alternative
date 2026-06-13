(ns social.mushin.alternative.application.custom
  (:require [social.mushin.alternative.db.custom :as custom]
            [social.mushin.alternative.application.depot :as depot]))
            

(defn upsert-custom
  [depot depot-opts actor-id-or-nickname owner-id label category value]
  ;; TODO authorize actor-id-or-nickname
  (depot/compose-and-transact-txs depot depot-opts (depot/upsert-custom depot (custom/create-custom owner-id label category value) depot-opts)))

(defn delete-custom
  [depot depot-opts actor-id-or-nickname owner-id label category]
  ;; TODO authorize actor-id-or-nickname
  (depot/compose-and-transact-txs depot depot-opts (depot/delete-custom depot owner-id label category depot-opts)))

(defn get-custom
  [depot depot-opts actor-id-or-nickname owner-id label category]
  ;; TODO authorize actor-id-or-nickname
  (depot/compose-and-transact-txs depot depot-opts (depot/get-custom-by-label depot owner-id label category depot-opts)))
