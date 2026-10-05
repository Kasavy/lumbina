package dev.lumbina.tv.core.domain.access

/** The apps the user lets hear the remote. */
val AccessSettings.micAllowed: Set<String> get() = micApps.filterValues { it }.keys

/**
 * Whether to ask the user about [app], which has started to record: only about the app on screen
 * ([inFront]), and only once. An app recording in the background does it by itself, not because
 * the user pressed its microphone button.
 */
fun AccessSettings.asksAboutMic(app: String, inFront: String?): Boolean = app == inFront && app !in micApps

fun AccessSettings.withMicApp(app: String, allowed: Boolean): AccessSettings =
    copy(micApps = micApps + (app to allowed))

/** Forgets the answer about [app]: it is asked about again when it next records. */
fun AccessSettings.withoutMicApp(app: String): AccessSettings = copy(micApps = micApps - app)
