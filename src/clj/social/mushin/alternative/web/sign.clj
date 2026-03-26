(ns social.mushin.alternative.web.sign
  (:require [integrant.core :as ig]
            [buddy.core.keys :as sign-keys]
            [clojure.tools.logging :as log]
            [kit.ig-utils :as ig-utils]))

(defmethod ig/init-key :social.mushin.alternative.web.sign/jwk [_ {:keys [priv pub]}]
  (log/info "Initializing the JWK token setup...")
  {:priv (sign-keys/private-key priv)
   :pub (sign-keys/public-key pub)})


(defmethod ig/suspend-key! :social.mushin.alternative.web.sign/jwk [_ _]
  (log/info "Suspending JWK token..."))

(defmethod ig/resume-key :social.mushin.alternative.web.sign/jwk
  [key opts old-opts old-impl]
  (ig-utils/resume-handler key opts old-opts old-impl))
