(ns social.mushin.alternative.web.controllers.i
  (:require [social.mushin.alternative.web.utils :refer [def-api-controller] :as web-utils]
            [social.mushin.alternative.db.depot :as depot]
            [social.mushin.alternative.db.users :as users]
            [ring.util.http-response :refer [bad-request! created ok unauthorized! conflict! no-content]]))
            
            
(def deactivate-user-body-schema
  [:map
   [:nickname :string]
   [:password :string]])

(def-api-controller
 who-am-i
 "Get the current with from their session."
 [{:keys [depot]}
  {{:keys [user-id]} :session}]
 (if-let [user-doc (depot/get-user-by-id depot user-id)]
   (ok user-doc)
   (web-utils/no-account-error user-id)))



(def-api-controller
 deactivate-user
 "Delete a user from the system."
 ;; TODO we need to make sure that the user being deleted is actually the user with this session.
 [{:keys [depot]}
  {{:keys [user-id]} :session {{:keys [nickname password]} :body} :parameters}]
 (let [credential-check-result (depot/check-nickname-and-password depot nickname password)]
   (if (uuid? credential-check-result)
     (if (= credential-check-result user-id)
       (do
         (depot/deactivate-user depot nickname password)
         (no-content))
       (web-utils/unauthorized user-id))
     (case credential-check-result
       :no-account (web-utils/no-account-error nickname)
       :wrong-nickname-or-password (web-utils/wrong-nickname-or-password nickname)
       :timeout (web-utils/user-timeout nickname)))))

