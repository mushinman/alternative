(ns social.mushin.alternative.web.middleware.formats
  (:require
   [cognitect.transit :as transit]
   [luminus-transit.time :as time]
   [muuntaja.core :as m]))

(defn- safe-key [s] (or (find-keyword s) s))

(def ^:private safe-keyword-handler
  (transit/read-handler safe-key))

(def instance
  (m/create
   (-> m/default-options
       (update :formats dissoc "application/edn")
       (assoc-in [:formats "application/json" :decoder 1 :decode-key-fn] safe-key)
       (update-in
        [:formats "application/transit+json" :decoder-opts]
        (partial merge time/time-deserialization-handlers))
       (update-in
        [:formats "application/transit+json" :decoder-opts :handlers]
        merge {":" safe-keyword-handler})
       (update-in
        [:formats "application/transit+json" :encoder-opts]
        (partial merge time/time-serialization-handlers)))))
