(ns social.mushin.alternative.application.custom
  (:require [social.mushin.alternative.db.custom :as custom]
            [social.mushin.alternative.executor :as exec]
            [social.mushin.alternative.application.database :as db]))
            
(defn upsert-custom
  "Create a custom document with `category`, `label`, and `value`. `value` is a JSON formatted string.

  `creator-id` must have permission to create documents for `owner-id`.

  Returns a tuple with the following columns:
  - Either the id of the document, or a `Future` to that value if `async?` is true.
  - The category of the custom document.
  - The label of the custom document."
  [{:keys [db db-opts executor]} creator-id owner-id label category value async?]
  ;; TODO authorize actor-id-or-nickname
  (let [impl (fn []
               (db/transact
                db
                (fn [db]
                  (db/create-custom-doc! db creator-id category label value db-opts)
                  (let [doc-id (:doc-id (db/get-custom-doc db category label db-opts))]
                    ;; Reassign ownsership.
                    (when-not (= creator-id owner-id)
                      (db/assign-doc! db creator-id owner-id doc-id db-opts))
                    doc-id))))]
    [(if async?
       (exec/submit executor impl)
       (impl))
     category label]))

(defn delete-custom
  [{:keys [db db-opts executor]} deleter-id label category hard-delete async?]
  ;; TODO authorize actor-id-or-nickname
  (let [impl (fn []
               (db/transact
                db
                (fn [db]
                  (let [doc-id (:doc-id (db/get-custom-doc db category label db-opts))]
                    (if hard-delete
                      (db/delete-doc! db doc-id db-opts)
                      (db/invalidate-doc! db doc-id deleter-id db-opts))
                    doc-id))))]
    [(if async?
       (exec/submit executor impl)
       (impl))
     category label]))

(defn get-custom
  [{:keys [db db-opts]} viewer-id label category]
  ;; TODO authorize actor-id-or-nickname
  (db/get-custom-doc db category label db-opts))
