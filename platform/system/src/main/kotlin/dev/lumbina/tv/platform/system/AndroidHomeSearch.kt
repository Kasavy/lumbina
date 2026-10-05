package dev.lumbina.tv.platform.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.text.HomeSearch
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.domain.text.YouTube
import javax.inject.Inject

/**
 * The system's search where there is one (Google TV and Android TV answer ACTION_ASSIST, as
 * their own launchers use it), else YouTube in an app for it. Some firmware has neither an
 * assistant nor a search app that takes a query from outside, so it gets YouTube or no button.
 */
internal class AndroidHomeSearch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youTube: YouTube,
    private val log: LumbinaLog,
) : HomeSearch {

    override suspend fun target(): SearchTarget? = when {
        assist().resolveActivity(context.packageManager) != null -> SearchTarget.System
        youTube.app() != null -> SearchTarget.YouTube
        else -> null
    }

    override fun openSystem(): Boolean = try {
        context.startActivity(assist())
        true
    } catch (e: ActivityNotFoundException) {
        log.log(TAG, "no system search: $e")
        false
    }

    private fun assist() = Intent(Intent.ACTION_ASSIST)
        .putExtra(SEARCH_TYPE, SEARCH_TYPE_KEYBOARD)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private companion object {
        const val TAG = "search"

        // What the stock Android TV launcher passes: 2 opens the keyboard, 1 would start listening.
        const val SEARCH_TYPE = "search_type"
        const val SEARCH_TYPE_KEYBOARD = 2
    }
}
