(ns social.mushin.alternative.db.xtdb.statuses
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [assert-not-exists-tx]]))

(defn insert-status-tx
  "Create an insertion transaction for `status`."
  [{:keys [xt/id] :as status}]
  [(assert-not-exists-tx
    (xt/template (fn [id]
                   (-> (from :mushin.db/statuses [{:xt/id id}])
                       (limit 1))))
    id)
   [:put-docs :mushin.db/statuses status]])
