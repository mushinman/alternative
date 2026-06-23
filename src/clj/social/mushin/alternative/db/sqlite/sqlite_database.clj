(ns social.mushin.alternative.db.sqlite.sqlite-database
  (:require [social.mushin.alternative.application.database :refer [AlternativeDatabase] :as db]
            [social.mushin.alternative.db.users :as users]
            [social.mushin.alternative.db.util :refer [blob->uuid]]
            [cheshire.core :as json]
            [social.mushin.alternative.crypt.password :as hash]
            [clj-uuid :as uuid]
            [honey.sql :as sql]
            [java-time.api :as time]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as jdbc-sql]))

(defrecord SqliteDatabase
    [ds]
  AlternativeDatabase

  (db-time [_ opts]
    (jdbc/execute-one! ds (sql/format {:select [:current-timestamp]})))

  (transact [_ callable]
    (jdbc/with-transaction [tx ds]
      (callable (SqliteDatabase. tx))))

  (alloc-doc! [_ actor-id opts]
    (jdbc/with-transaction [tx ds]
      (let [doc-id (uuid/v7)]
        (jdbc-sql/insert! tx :document-identities {:doc-id doc-id :owner actor-id})
        doc-id)))

  (invalidate-doc! [_ doc-id invalidator-id opts]
    (jdbc/with-transaction [tx ds]
      (jdbc-sql/insert! tx :document-metadata-deltas {:doc-id doc-id
                                                      :creator invalidator-id
                                                      :version (uuid/v7)
                                                      :doc-status "invalid"})))

  (assign-doc! [_ assignor-id assignee-id doc-id opts]
    (jdbc/with-transaction [tx ds]
      (jdbc-sql/insert! tx :document-metadata-deltas {:doc-id doc-id
                                                      :creator assignor-id
                                                      :version (uuid/v7)
                                                      :owner assignee-id})))

  (delete-doc! [_ doc-id opts]
    (jdbc/with-transaction [tx ds]
      (jdbc-sql/delete! tx :document-identities doc-id)))

  (get-roles-for [_ actor-id opts]
    (jdbc/execute! ds (sql/format {:select [:rd.doc-id :rd.version :rd.created-at
                                            :di.created-at :di.owner :di.doc-status
                                            :rd.creator :rd.doc]
                                   :from [[:role-documents :rd]]
                                   :join [:document-identites [:= :rd.doc-id :di.doc-id]]
                                   :where [:= [:json_extract :rd.doc "$.role-asignee"] actor-id]})))

  (get-custom-doc [_ category label opts]
    (when-let [{:keys [doc-id version created-at creator doc] :as custom-doc}
               (jdbc/execute-one! ds 
                                  (sql/format {:select [:doc-id :version :created-at :creator
                                                        :category :label :doc]
                                               :from [[:custom-documents :cd]]
                                               :where [:and
                                                       [:exists {:select [1]
                                                                 :from [[:document-identities :di]]
                                                                 :where [:and
                                                                         [:= :di.doc-id :cd.doc-id]
                                                                         [:= :di.doc-status "valid"]]}]
                                                       [:= :cd.category category]
                                                       [:= :cd.label label]
                                                       [:= :cd.version :%max.version]]}))]
      (merge
       custom-doc
       {:doc-id (blob->uuid doc-id)
        :version (blob->uuid version)
        :created-at (time/local-date-time created-at)
        :creator (blob->uuid creator)
        :doc (json/parse-string doc)})))

  (create-custom-doc!
    [_ creator-id category label json-value opts]
    (jdbc/with-transaction [tx ds]
      (let [tmp-db (SqliteDatabase. tx)
            doc-id (or (:doc-id (db/get-custom-doc tmp-db category label opts))
                       (db/alloc-doc! tmp-db creator-id opts))]
        (jdbc-sql/insert! tx :custom-documents {:doc-id doc-id
                                                :version (uuid/v7)
                                                :creator creator-id
                                                :category category
                                                :label label
                                                :doc json-value}))))

  (user-exists? [_ nickname opts]
    (some? (jdbc/execute-one! ds 
                              (sql/format {:select [1]
                                           :from [:user-documents]
                                           :where [:and
                                                   [:in :version {:select [[:%max.version]]
                                                                  :from [:user-documents]
                                                                  :group-by [:doc-id]}]
                                                   [:= [:json_extract :doc "$.nickname"] nickname]]
                                           :limit 1}))))

  (create-actor! [_ opts]
    (jdbc/with-transaction [tx ds]
      (let [actor-id (uuid/v7)]
        (jdbc-sql/insert! tx :actors {:id actor-id})
        actor-id)))

  (-create-password-for! [_ doc-id raw-password for-actor-id opts]
    ;; Ensure there's only one password.
    (jdbc/with-transaction [tx ds]
      (jdbc/execute! tx (sql/format {:delete-from [:document-identities]
                                     :where [:in :doc-id {:select [:au.doc-id]
                                                          :from [[:authentication-documents :au]] 
                                                          :join [:document-identities [:= :au.doc-id :doc-id]]
                                                          :where [:= :document-identities.owner for-actor-id]}]}))
      (jdbc-sql/insert! tx :authentication-documents {:doc-id doc-id
                                                      :authn-type "password"
                                                      :creator for-actor-id
                                                      :payload (json/generate-string {:hash (hash/hash-password raw-password)})})))

  
  (-create-user! [_ creator-id doc-id user-doc upsert? opts]
    (jdbc/with-transaction [tx ds]
      (let [existing (when upsert?
                       (some-> (jdbc/execute-one! tx (sql/format {:select [:doc]
                                                                  :from [:user-documents]
                                                                  :where [:= :doc-id doc-id]
                                                                  :order-by [[:version :desc]]
                                                                  :limit 1}))
                               vals first
                               (json/parse-string true)))
            merged (if existing (merge existing user-doc) user-doc)]
        (when-not (:nickname merged)
          (throw (ex-info "nickname field is required" {:doc-id doc-id})))
        (when (and existing (not= (:nickname existing) (:nickname merged)))
          (throw (ex-info "nickname cannot be changed" {:doc-id doc-id
                                                        :old (:nickname existing)
                                                        :new (:nickname merged)})))
        (when (some? (jdbc/execute-one! tx (sql/format {:select [1]
                                                        :from [:user-documents]
                                                        :where [:and
                                                                [:!= :doc-id doc-id]
                                                                [:in :version {:select [[:%max.version]]
                                                                               :from [:user-documents]
                                                                               :group-by [:doc-id]}]
                                                                [:= [:json_extract :doc "$.nickname"] (:nickname merged)]]
                                                        :limit 1})))
          (throw (ex-info "nickname already taken" {:nickname (:nickname merged)})))
        (jdbc-sql/insert! tx :user-documents {:doc-id doc-id
                                          :version (uuid/v7)
                                          :creator creator-id
                                          :doc (json/generate-string merged)}))))



  (insert-audit! [_ audit-doc opts]))

(defn create-sqlite-database
  [ds]
  (SqliteDatabase. ds))
