(ns social.mushin.alternative.application.custom
  (:require [social.mushin.alternative.db.custom :as custom]
            [social.mushin.alternative.application.depot :as depot]))
            

(defn upsert-custom
  [depot actor-id-or-nickname owner-id label category value]
  ;; TODO authorize actor-id-or-nickname
  (depot/upsert-custom depot (custom/create-custom owner-id label category value) {}))

(defn delete-custom
  [depot actor-id-or-nickname owner-id label category]
  ;; TODO authorize actor-id-or-nickname
  (depot/delete-custom depot owner-id label category {}))

(defn get-custom
  [depot actor-id-or-nickname owner-id label category]
  ;; TODO authorize actor-id-or-nickname
  (depot/get-custom-by-label depot owner-id label category {}))
