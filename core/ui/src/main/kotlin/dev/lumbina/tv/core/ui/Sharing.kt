package dev.lumbina.tv.core.ui

import kotlinx.coroutines.flow.SharingStarted

/**
 * How view models share their state with the screen: stops shortly after the screen goes away,
 * but not for a configuration change or a quick trip to another screen.
 */
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L)
