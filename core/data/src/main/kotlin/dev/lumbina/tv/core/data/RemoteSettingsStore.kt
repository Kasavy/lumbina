package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.data.home.decode
import dev.lumbina.tv.core.data.home.encode
import dev.lumbina.tv.core.domain.remote.RemoteSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredRemoteSettings(val doubleHomeRecents: Boolean = true, val otherHome: String? = null)

internal fun StoredRemoteSettings.toDomain() = RemoteSettings(doubleHomeRecents, otherHome?.let(::decode))

internal fun RemoteSettings.toStored() = StoredRemoteSettings(doubleHomeRecents, otherHome?.encode())
