package dev.lumbina.tv.platform.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.text.YouTube
import dev.lumbina.tv.core.domain.text.YouTubeSearch
import javax.inject.Inject

/** Searches YouTube in the app chosen for it, by opening a search link there. */
internal class AndroidYouTubeSearch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTube: YouTube,
    private val log: LumbinaLog,
) : YouTubeSearch {

    override suspend fun search(query: String): Boolean {
        val app = youTube.app() ?: return false
        return open(app.packageName, query)
    }

    override suspend fun searchIn(packageName: String, query: String): Boolean =
        youTube.isOne(packageName) && open(packageName, query)

    private fun open(packageName: String, query: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, youTubeSearchLink(query))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            log.log(TAG, "$packageName not available: $e")
            false
        }
    }

    private companion object {
        const val TAG = "search"
    }
}
