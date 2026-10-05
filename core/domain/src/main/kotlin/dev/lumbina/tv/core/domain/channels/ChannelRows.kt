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

private const val MIX_KEY = "mix"
private const val MIX_SIZE = 30
