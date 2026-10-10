package dev.lumbina.tv.platform.system

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.text.SystemSearch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The system's search where there is one: Google TV and Android TV answer ACTION_ASSIST, as their
 * own launchers use it. Some firmware has neither an assistant nor a search app that takes a
 * query from outside.
 */
internal class AndroidSystemSearch @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : SystemSearch {

    override suspend fun available(): Boolean = withContext(Dispatchers.IO) {
        assist().resolveActivity(context.packageManager) != null
    }

    override fun open(): Boolean = start(assist())

    /** Started without resolving first: resolving needs the search's package to be visible. */
    override fun search(query: String): Boolean = start(
        Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        log.log(TAG, "no system search for ${intent.action}: $e")
        false
    } catch (e: SecurityException) {
        log.log(TAG, "system search for ${intent.action} not allowed: $e")
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
