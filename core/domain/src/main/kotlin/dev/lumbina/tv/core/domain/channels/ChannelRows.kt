package dev.lumbina.tv.core.domain.channels

/** A channel row on the home screen: the app by its name, the channel and its programs. */
data class ChannelRow(val key: String, val app: String, val channel: String, val programs: List<ProgramCard>) {
    /** The mix of all apps' programs, shown first. */
    val isMix: Boolean get() = key == MIX_KEY
}

/**
 * The channels shown, in this order, after a mix of them when they come from more than one app.
 * Channels the user hid and those of apps not in [labels] (package to name) are left out.
 */
fun List<ChannelFeed>.visibleRows(settings: ChannelSettings, labels: Map<String, String>): List<ChannelRow> {
    val shown = filter { it.channel.key !in settings.hiddenChannels && it.channel.packageName in labels }
    val rows = shown.map { feed ->
        ChannelRow(feed.channel.key, labels.getValue(feed.channel.packageName), feed.channel.name, feed.programs)
    }
    val apps = shown.map { it.channel.packageName }.distinct().size
    return if (apps > 1) listOf(ChannelRow(MIX_KEY, "", "", mixedPrograms(shown, MIX_SIZE))) + rows else rows
}

/**
 * "Continue watching" as home shows it: programs of installed apps ([labels], package to name) the
 * user did not take out of it.
 */
fun List<ProgramCard>.shownWatchNext(settings: ChannelSettings, labels: Map<String, String>): List<ProgramCard> =
    filter { it.packageName in labels && it.packageName !in settings.hiddenWatchNext }

/**
 * The installed apps with programs in "continue watching", taken out of the row or not: they are
 * being watched all the same, which puts their channels first ([byAppUse]).
 */
fun List<ProgramCard>.watchedApps(labels: Map<String, String>): Set<String> =
    mapNotNullTo(mutableSetOf()) { card -> card.packageName.takeIf { it in labels } }

/**
 * The channels in the user's [order] (their keys), as when each has a row of its own; the ones it
 * does not name keep the order they came in, after the others.
 */
fun <T> List<T>.inChannelOrder(order: List<String>, key: (T) -> String): List<T> {
    val place = order.withIndex().associate { (index, channel) -> channel to index }
    return sortedBy { place[key(it)] ?: Int.MAX_VALUE }
}

/** [channel] moved [by] places among these channels (as shown now); the result names them all. */
fun List<String>.movedChannel(channel: String, by: Int): List<String> {
    val from = indexOf(channel)
    if (from < 0) return this
    val to = (from + by).coerceIn(0, lastIndex)
    return toMutableList().apply { add(to, removeAt(from)) }
}

private const val MIX_KEY = "mix"
private const val MIX_SIZE = 30
