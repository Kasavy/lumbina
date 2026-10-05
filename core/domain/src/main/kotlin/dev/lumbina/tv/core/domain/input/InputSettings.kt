package dev.lumbina.tv.core.domain.input

import dev.lumbina.tv.core.domain.settings.SettingsRepository

/**
 * How text is typed in Lumbina's dialogs. [useLumbinaKeyboard]: Lumbina's own on-screen keyboard
 * (Russian and English on any device); off: the device's keyboard, which on some firmware has
 * no Russian layout.
 */
data class InputSettings(val useLumbinaKeyboard: Boolean = true)

typealias InputSettingsRepository = SettingsRepository<InputSettings>
