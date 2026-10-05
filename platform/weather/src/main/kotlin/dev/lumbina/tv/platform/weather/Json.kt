package dev.lumbina.tv.platform.weather

import java.io.IOException
import kotlinx.serialization.json.Json

internal val lenientJson = Json { ignoreUnknownKeys = true }

/** Parses [text], turning malformed answers into [IOException] like other service failures. */
internal inline fun <reified T> decode(text: String): T = try {
    lenientJson.decodeFromString<T>(text)
} catch (e: IllegalArgumentException) { // SerializationException included
    throw IOException("unexpected answer", e)
}
