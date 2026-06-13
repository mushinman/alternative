package social.mushin.alternative.hosted.db

import social.mushin.alternative.hosted.application.error.AlternativeException
import clojure.lang.ExceptionInfo
import clojure.lang.IPersistentMap
import clojure.lang.PersistentArrayMap
import clojure.lang.Keyword

class AlternativeCacheMissException(
    message: String,
    code: Keyword,
    data: IPersistentMap = PersistentArrayMap.EMPTY
) : AlternativeException(message, code, data)
