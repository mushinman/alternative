(ns social.mushin.alternative.db.xtdb.statuses
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [assert-not-exists-tx]]
            [clj-uuid :as uuid]
            [honey.sql :as sql]
            [buddy.hashers :as hashers]
            [social.mushin.alternative.db.users :as base-users]
            [social.mushin.alternative.crypt.password :as crypt]
            [java-time.api :as jt]
            [social.mushin.alternative.files :refer [coerce-to-host-uri coerce-to-uri]]
            [social.mushin.alternative.utils :refer [to-java-uri]]))

(defn insert-status-tx
  "Create an insertion transaction for `status`."
  [{:keys [xt/id] :as status}]
  [(assert-not-exists-tx
    (xt/template (fn [id]
                   (-> (from :mushin.db/users [{:xt/id id}])
                       (limit 1))))
    id)
   [:put-docs :mushin.db/statuses status]])
