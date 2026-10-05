package dev.lumbina.tv.tvprovider

import android.app.Activity
import android.os.Bundle

/**
 * `TvContractCompat.requestChannelBrowsable` asks the launcher to show an app's channel.
 * Channels are already browsable here, so just log the request and say yes.
 */
class RequestBrowsableActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompanionLog.init(this)
        CompanionLog.log("$callingPackage asks to show a channel: ${intent.extras?.keySet()}")
        setResult(RESULT_OK)
        finish()
    }
}
