(ns social.mushin.alternative.db.xtdb.custom
  (:require [xtdb.api :as xt]
            [social.mushin.alternative.db.xtdb.util :refer [delete-where]]))


(defn delete-custom-tx-part
  [label category owner-id]
  ;; TODO when xt/2 supports tuple keys we can change this.
  (delete-where
   :mushin.db/custom
   (xt/template
    (fn [label category]
      (-> (from :mushin.db/custom [{:label label :category category :owner-id owner-id}])
          (limit 1))))
   label category owner-id))

(defn upsert-custom-tx
  [{:keys [label category owner-id] :as custom}]
  ;; TODO when xt/2 supports tuple keys we can change this.
  [(delete-custom-tx-part label category owner-id)
   [:put-docs :mushin.db/custom custom]])

(defn get-custom-by-label
  [db-con label category owner-id opts]
  (first
   (xt/q [(xt/template
           (fn [label category]
             (-> (from :mushin.db/custom [* {:label label :category category :owner-id owner-id}])
                 (limit 1)
                 (without :xt/id))))
          label category owner-id]
         db-con
         opts)))
