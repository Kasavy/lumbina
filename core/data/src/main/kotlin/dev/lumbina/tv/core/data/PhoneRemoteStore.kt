package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredPhoneRemoteSettings(
    val enabled: Boolean = false,
    val key: String = "",
    val onScreensaver: Boolean = false,
)

internal fun StoredPhoneRemoteSettings.toDomain() = PhoneRemoteSettings(enabled, key, onScreensaver)

/** An empty key is replaced by a new one, so the address never has an empty one. */
internal fun PhoneRemoteSettings.toStored() =
    StoredPhoneRemoteSettings(enabled, key.ifEmpty { PhoneRemoteSettings.newKey() }, onScreensaver)
