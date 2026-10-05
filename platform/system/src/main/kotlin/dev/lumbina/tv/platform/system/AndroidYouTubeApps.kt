package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.text.YouTubeApp
import dev.lumbina.tv.core.domain.text.YouTubeApps
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The apps that take a YouTube search link: those that answer it and are not browsers (a browser
 * answers a link to any site). The official app comes first, the others by name. The list is
 * asked for with every search and every phrase said, so it is kept until some package changes.
 */
@Singleton
internal class AndroidYouTubeApps @Inject constructor(@ApplicationContext private val context: Context) :
    YouTubeApps {

    private val mutex = Mutex()
    private var known: List<YouTubeApp>? = null

    /** Where the system's record of package changes was read up to. */
    private var changesSeen = 0

    override suspend fun installed(): List<YouTubeApp> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val changes = context.packageManager.getChangedPackages(changesSeen)
            if (changes != null) changesSeen = changes.sequenceNumber
            known?.takeIf { changes == null } ?: find().also { known = it }
        }
    }

    private fun find(): List<YouTubeApp> {
        val browsers = handlers(ANY_SITE)
        return handlers(youTubeSearchLink("a").toString())
            .filterNot { it in browsers || it == context.packageName }
            .map { YouTubeApp(it, context.packageManager.labelOf(it)) }
            .sortedWith(compareByDescending<YouTubeApp> { it.packageName.startsWith(OFFICIAL) }.thenBy { it.label })
    }

    private fun handlers(link: String): Set<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
        return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private companion object {
        const val ANY_SITE = "https://example.com/"
        const val OFFICIAL = "com.google.android.youtube"
    }
}

/** The address of YouTube's results for [query]: clients show the results even when started by it. */
internal fun youTubeSearchLink(query: String): Uri = Uri.parse("https://www.youtube.com/results").buildUpon()
    .appendQueryParameter("search_query", query)
    .build()
