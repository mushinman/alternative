;; This code is adapted from biff's xtdb v2 helper code found here:
;; https://github.com/jacobobryant/biff/blob/d71b8c2422978e070838214d594b716bbd30e11d/libs/xtdb2/src/com/biffweb/xtdb.clj

(ns social.mushin.alternative.db.xtdb.util
  (:require [malli.core :as malli]
            [honey.sql.helpers :as h]
            [honey.sql :as sql]
            [xtdb.api :as xt])
  (:import [xtdb.api Xtdb]))


(def ^:private xtdb-node?
  [:fn #(instance? Xtdb %)])

(defn check-args* [& arg-maps]
  (doseq [{:keys [value schema quoted-schema]} arg-maps
          :when (not (malli/validate schema value))]
    (throw (ex-info (str "Invalid argument: "
                         (pr-str value) " doesn't satisfy schema `"
                         (pr-str quoted-schema) "`")
                    {:argument value
                     :schema quoted-schema}))))

(defmacro check-args [& args]
  (when-not (even? (count args))
    (throw (clojure.lang.ArityException. (+ (count args) 2) "check-args")))
  `(check-args* ~@(for [[value schema] (partition 2 args)]
                    {:value value
                     :schema schema
                     :quoted-schema (list 'quote schema)})))

(defmacro check-arity [valid n-args fn-name]
  `(when-not ~valid
     (throw (clojure.lang.ArityException. ~n-args ~fn-name))))

(defn- check-lookup-args [fn-name node table kvs]
  (check-arity (even? (count kvs)) (+ (count kvs) 2) fn-name)
  (check-args node xtdb-node? table :keyword))

(defn check-attr-schema [attr schema value]
  (when-not (malli/validate schema value)
    (throw (ex-info "Value doesn't match attribute schema"
                    {:attr attr
                     :schema schema
                     :value value
                     :errors (:errors (malli/explain schema value))}))))

(defn check-table-schema [table document]
  (when-not (malli/validate table document)
    (throw (ex-info "Document doesn't match table schema"
                    {:table table
                     :document document
                     :errors (:errors (malli/explain table document))}))))

(defn -malli-wrap [{:keys [value message] :as error}]
  (str "Invalid argument `" (pr-str value) "`: " message))

(defn erase-where
  "Create a SQL transaction from a XTQL query that erases all the documents returned by the query.
  `args` is for arguments to the query."
  [table query & args]
  (sql/format (-> (h/erase-from table)
                  (h/where
                   [:exists
                    (into [:xtql query] args)]))))

(defn delete-where
  "Create a SQL transaction from a XTQL query that deletes all the documents returned by the query.
  `args` is for arguments to the query."
  [table query & args]
  (sql/format (-> (h/delete-from table)
                  (h/where
                   [:exists
                    (into [:xtql query] args)]))))


(defn assert-not-exists-tx
  "Assert that no row matches the xtql `query`.

  When transacted, if the ASSERTion fails, the whole transaction will be aborted."
  [query & args]
  (let [[q & ps] (sql/format {:assert [:not-exists (into [:xtql query] args)]})]
    (into [q] ps)))

(defn assert-exists-tx
  "Assert that no row matches the xtql `query`.

  When transacted, if the ASSERTion fails, the whole transaction will be aborted."
  [query & args]
  (let [[q & ps] (sql/format {:assert [:exists (into [:xtql query] args)]})]
    (into [q] ps)))


(defn compile-op-dispatch [node op]
  (first op))

(defmulti compile-op #'compile-op-dispatch)

(defmethod compile-op :default
  [_ op]
  [op])

(defmethod compile-op :sql
  [_ op]
  [op])


;; TODO this is bad. We should just check to see if the document exists
;; and reject tx if it's an insert. If it's an update, we'll check against the schema
;; but ignore missing parts of the doc.
;; Alternatively, I could just turn off schema checking for release mode...
(defmethod compile-op :patch-docs
  [node [_ table-or-opts & docs :as op]]
  ;; If the patch operation is an UPDATE: we can be sure that the document
  ;; already in the DB is of the correct format.
  ;; If the patch is an INSERT: the document will be incorrect if it has any
  ;; missiing fields. So we combine the documents and then submit.
  (let [table (if (map? table-or-opts)
                (:into table-or-opts)
                table-or-opts)]
    (doseq [doc docs]
      (let [cur-doc 
            (first (xt/q node (xt/template (-> (from ~table [* {:xt/id ~id}])
                                               (limit 1)))))
            new-doc (if cur-doc
                      (merge cur-doc doc)
                      doc)]
        (check-table-schema table new-doc)))
    [op]))

(defmethod compile-op :put-docs
  [_ [_ table-or-opts & docs :as op]]
  (let [table (if (map? table-or-opts)
                (:into table-or-opts)
                table-or-opts)]
    (doseq [doc docs]
      (check-table-schema table doc))
    [op]))

(defn compile-tx [node local-tx]
  (reduce (fn [tx op]
            (into tx (compile-op node op)))
          []
          local-tx))

(defn submit-tx
  ([node local-tx opts]
   (xt/submit-tx node (compile-tx node local-tx) opts))
  ([node local-tx]
   (submit-tx node local-tx)))

(defn execute-tx
  ([node local-tx opts]
   (xt/execute-tx node (compile-tx node local-tx) opts))
  ([node local-tx]
   (xt/execute-tx node (compile-tx node local-tx))))

(defn compose-txs
  "Combine XTDB transaction vectors into a single transaction.

  Each argument can be a single statement (e.g.
  `[:put-docs :mushin.db/users {:xt/id (random-uuid)}]`) or a vector of statements
  (e.g. [[:put-docs :mushin.db/users {:xt/id (random-uuid)}] [:sql 'DELETE FROM likes']]),
  or nil."
  [& txs]
  (into []
   (comp (map
          (fn [tx]
            (cond
              (nil? tx) tx
              (and (vector? tx) (vector? (first tx))) tx
              (vector? tx) [tx]
              :else nil)))
         (remove nil?)
         cat)
   txs))

(defn db-time
  "Get the current time on the database."
  ([db-con opts]
   (-> (xt/q db-con ["SELECT CURRENT_TIMESTAMP"] opts)
       first
       :xt/column-1))
  ([db-con]
   (db-time db-con {})))
