package dev.lumbina.tv.core.domain.channels

/**
 * Channels of the most used apps first ([usage]: openings per package); among apps with
 * no usage known, those with something in "continue watching" ([watching]) first. Otherwise the
 * published order stays.
 */
fun List<ChannelFeed>.byAppUse(usage: Map<String, Long>, watching: Set<String>): List<ChannelFeed> = sortedWith(
    compareByDescending<ChannelFeed> { usage[it.channel.packageName] ?: 0L }
        .thenByDescending { it.channel.packageName in watching },
)

/**
 * A mix of all the apps' programs: one from each app in turn, in the order of [feeds] (so the
 * most used app leads), each app's channels one after another. At most [limit] programs.
 */
fun mixedPrograms(feeds: List<ChannelFeed>, limit: Int): List<ProgramCard> {
    val perApp = feeds.groupBy { it.channel.packageName }.values
        .map { channels -> channels.flatMap { it.programs }.distinctBy { it.id }.iterator() }
    val mixed = mutableListOf<ProgramCard>()
    while (mixed.size < limit && perApp.any { it.hasNext() }) {
        perApp.forEach { programs -> if (programs.hasNext() && mixed.size < limit) mixed += programs.next() }
    }
    return mixed
}
