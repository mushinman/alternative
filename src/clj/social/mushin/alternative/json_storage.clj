(ns social.mushin.alternative.json-storage
  (:require [jsonista.core :as json]
            [java-time.api :as time]
            [social.mushin.alternative.codecs :as codecs])
  (:import [com.fasterxml.jackson.databind.module SimpleModule]
           [com.fasterxml.jackson.databind JsonSerializer JsonDeserializer
            SerializerProvider DeserializationContext JsonNode]
           [com.fasterxml.jackson.core JsonGenerator JsonParser]
           [java.time ZonedDateTime LocalDateTime Instant OffsetDateTime
            MonthDay Year YearMonth Duration Period]
           [java.util UUID]))

(def ztd-serializer
  (proxy [JsonSerializer] []
    (serialize [^ZonedDateTime zdt ^JsonGenerator gen ^SerializerProvider _]
      (.writeStartObject gen)
      (.writeStringField gen "__type" "zoned-date-time")
      (.writeStringField gen "value" (str (time/instant zdt)))
      (.writeStringField gen "zone-id" (str (time/zone-id zdt)))
      (.writeEndObject gen))))

(def default-write-maps
  {ZonedDateTime (proxy [JsonSerializer] []
                   (serialize [^ZonedDateTime zdt ^JsonGenerator gen ^SerializerProvider _]
                     (.writeStartObject gen)
                     (.writeStringField gen "__type" "#datetime")
                     (.writeStringField gen "utc-value" (str (time/instant zdt)))
                     (.writeStringField gen "zone-id" (str (time/zone-id zdt)))
                     (.writeEndObject gen)))

   LocalDateTime (proxy [JsonSerializer] []
                   (serialize [^LocalDateTime ldt ^JsonGenerator gen ^SerializerProvider _]
                     (let [zdt (time/zoned-date-time ldt (time/zone-id))]
                       (.writeStartObject gen)
                       (.writeStringField gen "__type" "#datetime")
                       (.writeStringField gen "utc-value" (str (time/instant zdt)))
                       (.writeStringField gen "zone-id" (str (time/zone-id zdt)))
                       (.writeEndObject gen))))

   OffsetDateTime (proxy [JsonSerializer] []
                   (serialize [^OffsetDateTime odt ^JsonGenerator gen ^SerializerProvider _]
                     (.writeStartObject gen)
                     (.writeStringField gen "__type" "#datetime")
                     (.writeStringField gen "utc-value" (str (time/instant odt)))
                     (.writeStringField gen "zone-offset" (str (time/zone-offset odt)))
                     (.writeEndObject gen)))

   Duration (proxy [JsonSerializer] []
              (serialize [^Duration d ^JsonGenerator gen ^SerializerProvider _]
                (.writeStartObject gen)
                (.writeStringField gen "__type" "#duration")
                (.writeStringField gen "value" (str d))
                (.writeEndObject gen)))

   Period (proxy [JsonSerializer] []
            (serialize [^Period p ^JsonGenerator gen ^SerializerProvider _]
              (.writeStartObject gen)
              (.writeStringField gen "__type" "#period")
              (.writeStringField gen "value" (str p))
              (.writeEndObject gen)))

   Instant (proxy [JsonSerializer] []
             (serialize [^Instant instant ^JsonGenerator gen ^SerializerProvider _]
               (.writeStartObject gen)
               (.writeStringField gen "__type" "#datetime")
               (.writeStringField gen "utc-value" (str instant))
               (.writeEndObject gen)))

   MonthDay (proxy [JsonSerializer] []
              (serialize [^MonthDay o ^JsonGenerator gen ^SerializerProvider _]
                (.writeStartObject gen)
                (.writeStringField gen "__type" "#dayofmonth")
                (.writeStringField gen "value" (str o))
                (.writeEndObject gen)))

   Year (proxy [JsonSerializer] []
              (serialize [^Year o ^JsonGenerator gen ^SerializerProvider _]
                (.writeStartObject gen)
                (.writeStringField gen "__type" "#year")
                (.writeStringField gen "value" (str o))
                (.writeEndObject gen)))

   YearMonth (proxy [JsonSerializer] []
              (serialize [^YearMonth o ^JsonGenerator gen ^SerializerProvider _]
                (.writeStartObject gen)
                (.writeStringField gen "__type" "#monthofyear")
                (.writeStringField gen "value" (str o))
                (.writeEndObject gen)))

   UUID (proxy [JsonSerializer] []
          (serialize [^UUID o ^JsonGenerator gen ^SerializerProvider _]
            (.writeStartObject gen)
            (.writeStringField gen "__type" "#uuid")
            (.writeStringField gen "value" (str o))
            (.writeEndObject gen)))

   (Class/forName "[B") (proxy [JsonSerializer] []
                          (serialize [^bytes bs ^JsonGenerator gen ^SerializerProvider _]
                            (.writeStartObject gen)
                            (.writeStringField gen "__type" "#blob")
                            (.writeStringField gen "blob-encoding" "base64")
                            (.writeStringField gen "value" (codecs/bytes->b64 bs))
                            (.writeEndObject gen)))})

(def default-read-maps
  {;; Date types.
   "#datetime" (fn [^JsonNode node]
                 (let [instant (time/instant (.. node (get "utc-value") asText))]
                   (cond
                     (.has node "zone-id")
                     (time/zoned-date-time instant (time/zone-id (.. node (get "zone-id") asText)))

                     (.has node "zone-offset")
                     (time/offset-date-time instant (time/zone-offset (.. node (get "zone-offset") asText)))

                     :else
                     instant)))

   "#duration" (fn [^JsonNode node]
                 (time/duration (.. node (get "value") asText)))

   "#period" (fn [^JsonNode node]
               (time/period (.. node (get "value") asText)))

   "#dayofmonth" (fn [^JsonNode node]
                   (time/month-day (.. node (get "value") asText)))

   "#year" (fn [^JsonNode node]
             (time/year (.. node (get "value") asText)))

   "#monthofyear" (fn [^JsonNode node]
                    (time/year-month (.. node (get "value") asText)))

   ;; Binary blobs.
   "#blob" (fn [^JsonNode node]
             (let [value (.get node "value")]
               (case (.. node (get "blob-encoding") asText)
                 "ints" (byte-array (mapv (fn [^JsonNode i] (.asInt i)) value))
                 "base64" (codecs/b64->bytes (.asText value))
                 "base64-url" (codecs/b64u->bytes (.asText value)))))

   ;; Misc types.
   "#uuid" (fn [^JsonNode node]
             (parse-uuid (.. node (get "value") asText)))})

(defn- node->clj [custom-readers ^JsonNode node]
  (cond
    (and (.isObject node) (.has node "__type"))
    (if-let [custom-reader (custom-readers (.. node (get "__type") asText))]
      (custom-reader node)
      (throw (ex-info "Unknown __type" {:type (.. node (get "__type") asText)})))

    (.isObject node)
    (into {} (map (fn [e] [(.getKey e) (node->clj custom-readers (.getValue e))])
                  (iterator-seq (.fields node))))

    (.isArray node)
    (mapv (partial node->clj custom-readers) (iterator-seq (.elements node)))

    (.isTextual node)  (.asText node)
    (.isNumber node)   (.numberValue node)
    (.isBoolean node)  (.booleanValue node)
    (.isNull node)     nil))

(defn typed-deserializer
  [custom-readers]
  (proxy [JsonDeserializer] []
    (deserialize [^JsonParser p ^DeserializationContext _]
      (node->clj custom-readers (.readValueAsTree p)))))

(defn storage-module
  [custom-readers custom-writers]
  (let [module (doto (SimpleModule.)
                 (.addDeserializer Object (typed-deserializer custom-readers)))]
    (doseq [[writer-type writer-func] custom-writers]
      (.addSerializer module writer-type writer-func))
    module))

(def storage-mapper
  (json/object-mapper {:modules [(storage-module default-read-maps default-write-maps)]}))
