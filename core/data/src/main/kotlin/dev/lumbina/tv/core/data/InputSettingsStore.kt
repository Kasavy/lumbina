package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.input.InputSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredInputSettings(val useLumbinaKeyboard: Boolean = true)

internal fun StoredInputSettings.toDomain() = InputSettings(useLumbinaKeyboard)

internal fun InputSettings.toStored() = StoredInputSettings(useLumbinaKeyboard)
