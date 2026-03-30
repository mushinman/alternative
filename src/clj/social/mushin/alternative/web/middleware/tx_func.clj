(ns social.mushin.alternative.web.middleware.tx-func
  (:require [clojure.string :as cstr]
            [social.mushin.alternative.db.xtdb.util :as db]))

(defn wrap-add-tx-fn
  [handler]
  (fn [req]
    (handler (assoc req :mushin/async?
                       (cstr/includes? (str (get-in req [:headers "prefer"])) "respond-async")))))
