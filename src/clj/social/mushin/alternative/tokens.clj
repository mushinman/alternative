(ns social.mushin.alternative.tokens
  (:require [buddy.core.nonce :as nonce]
            [social.mushin.alternative.codecs :as codecs]))

(defn generate-token
  [n-bytes]
  (-> (nonce/random-bytes n-bytes)
      (codecs/bytes->b64u)))
