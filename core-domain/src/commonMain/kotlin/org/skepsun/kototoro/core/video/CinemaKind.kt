package org.skepsun.kototoro.core.video

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A declared video type. Unknown types stay available in the general video catalogue. */
enum class CinemaKind {
    MOVIE,
    SERIES,
    ;

    companion object {
        fun fromSourceData(sourceName: String, sourceData: String?): CinemaKind? {
            when (sourceName) {
                "AMURATSU_STARTER_MOVIES" -> return MOVIE
                "AMURATSU_STARTER_SERIES" -> return SERIES
            }
            if (!sourceName.startsWith("CLOUDSTREAM_") || sourceData.isNullOrEmpty()) return null
            val metadata = runCatching { Json.parseToJsonElement(sourceData) as? JsonObject }.getOrNull()
            return fromVideoType((metadata?.get("type") as? JsonPrimitive)?.content)
        }

        fun fromVideoType(type: String?): CinemaKind? = when (type) {
            "Movie" -> MOVIE
            "TvSeries", "AsianDrama" -> SERIES
            else -> null
        }
    }
}

enum class CinemaFilter {
    ONLY,
    EXCLUDE,
    ;

    fun accepts(kind: CinemaKind?): Boolean = (kind != null) == (this == ONLY)
}
