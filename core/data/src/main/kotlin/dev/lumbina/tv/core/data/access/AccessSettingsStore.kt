package dev.lumbina.tv.core.data.access

import dev.lumbina.tv.core.domain.access.AccessSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredAccessSettings(
    val closeFromRecents: Boolean = true,
    val openOnPowerOn: Boolean = false,
    val setupCardHidden: Boolean = false,
    val micApps: Map<String, Boolean> = emptyMap(),
    val setupReopened: Boolean = false,
)

internal fun StoredAccessSettings.toDomain() =
    AccessSettings(closeFromRecents, openOnPowerOn, setupCardHidden, micApps, setupReopened)

internal fun AccessSettings.toStored() =
    StoredAccessSettings(closeFromRecents, openOnPowerOn, setupCardHidden, micApps, setupReopened)
