package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import javax.inject.Inject

internal class AndroidLumbinaHome @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : LumbinaHome {

    override fun open(toStart: Boolean) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            ?.putExtra(LumbinaActions.EXTRA_TO_START, toStart)
        if (intent == null) {
            log.log(TAG, "no launch intent for ${context.packageName}")
            return
        }
        context.startActivity(intent)
    }

    private companion object {
        const val TAG = "home"
    }
}
