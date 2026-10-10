package dev.lumbina.tv.core.domain.channels

/**
 * What the TV Provider holds, app by app, for a diagnostics report: how many channels each app
 * published, how many of them the home screen leaves out and why, and how many programs. Where the
 * home screen shows no channels, this tells whether there are none or none passed.
 */
fun interface ChannelCensus {
    /**
     * A line per app, as far as Lumbina may read the provider; a line saying so when it holds
     * nothing or a table could not be read. Blocks while it reads.
     */
    fun take(): List<String>
}
