package dev.lumbina.tv.platform.weather

import dev.lumbina.tv.core.common.CYRILLIC_LANGUAGES
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.PlaceLocator
import dev.lumbina.tv.core.network.HttpGet
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlinx.serialization.Serializable

@Serializable
internal data class GeocodingAnswer(val results: List<Result> = emptyList()) {
    @Serializable
    data class Result(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
    )
}

@Serializable
internal data class IpWhoAnswer(
    val success: Boolean = false,
    val city: String? = null,
    val region: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
internal data class GeoJsAnswer(
    val city: String? = null,
    val region: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
)

/**
 * City search through the Open-Meteo geocoding service (names in the UI language) and a place
 * from the public IP: ipwho.is (named in the UI language where it knows it), with geojs.io, which
 * names places in English only, as a fallback. A VPN makes the IP place wrong, hence the setting
 * to choose a city.
 */
internal class IpPlaceLocator @Inject constructor(private val http: HttpGet) : PlaceLocator {

    override suspend fun byIp(language: String): Place? = orNull { ipWho(language) } ?: orNull { geoJs() }

    private inline fun orNull(lookup: () -> Place?): Place? = try {
        lookup()
    } catch (_: IOException) {
        null
    }

    private suspend fun ipWho(language: String): Place? {
        val answer = decode<IpWhoAnswer>(http.text(ipWhoUrl(language)))
        if (!answer.success) return null
        val city = answer.city ?: return null
        return Place(city, answer.latitude ?: return null, answer.longitude ?: return null, answer.region)
    }

    private suspend fun geoJs(): Place? {
        val answer = decode<GeoJsAnswer>(http.text("https://get.geojs.io/v1/ip/geo.json"))
        val city = answer.city ?: return null
        val latitude = answer.latitude?.toDoubleOrNull() ?: return null
        return Place(city, latitude, answer.longitude?.toDoubleOrNull() ?: return null, answer.region)
    }

    override suspend fun search(query: String): List<Place> {
        if (query.isBlank()) return emptyList()
        val name = URLEncoder.encode(query.trim(), "UTF-8")
        val language = searchLanguage(query)
        val answer = decode<GeocodingAnswer>(
            http.text("https://geocoding-api.open-meteo.com/v1/search?name=$name&count=$RESULTS&language=$language"),
        )
        return answer.results.map { r ->
            Place(r.name, r.latitude, r.longitude, listOfNotNull(r.admin1, r.country).joinToString().ifEmpty { null })
        }
    }

    private companion object {
        const val RESULTS = 6
    }
}

/** ipwho.is names the place in [uiLanguage] when it is one of those it knows, else in English. */
internal fun ipWhoUrl(uiLanguage: String): String {
    val language = if (uiLanguage in IP_WHO_LANGUAGES) "&lang=$uiLanguage" else ""
    return "https://ipwho.is/?fields=success,city,region,latitude,longitude$language"
}

private val IP_WHO_LANGUAGES = setOf("ru", "de", "es", "fr", "ja")

/**
 * The geocoder only matches names in the language it is asked for, so a Cyrillic query is
 * looked up in Russian whatever the UI language is.
 */
internal fun searchLanguage(query: String, uiLanguage: String = Locale.getDefault().language): String {
    val cyrillic = query.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC }
    return if (cyrillic && uiLanguage !in CYRILLIC_LANGUAGES) "ru" else uiLanguage
}
