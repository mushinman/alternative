(ns social.mushin.alternative.web.middleware.cache-control)

(defn wrap-cache-control
  [directives handler]
  (fn [req]
    (-> (handler req)
        (assoc-in [:headers "Cache-Control"] directives))))
