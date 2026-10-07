package dev.lumbina.tv.vendor.hisense

import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.DeviceDiagnostics

/**
 * What a report asks a Hisense firmware: mostly about the remote's mic, which is the part that
 * differs between its generations.
 */
internal class HisenseDiagnostics(stockLauncher: String) : DeviceDiagnostics {

    override val packages = listOf(stockLauncher, PCM_SHARE_PACKAGE)

    /** Whether HsPcmShare runs and listens (local socket, port), and which voice packages are installed. */
    override val shellCommands = listOf(
        "dumpsys activity services $PCM_SHARE_PACKAGE",
        "grep -i pcm /proc/net/unix",
        "netstat -ltn | grep ${TcpTransport.PORT}",
        "pm list packages | grep -i -E 'pcm|speech|voice|aiview|katniss'",
    )

    override fun probe(): List<DeviceAnswer> = PcmShareProbe(
        routes = listOf(
            "local socket" to { LocalSocketTransport.connect(readTimeoutMillis = TIMEOUT_MILLIS) },
            "port ${TcpTransport.PORT}" to { TcpTransport.connect(TIMEOUT_MILLIS) },
        ),
        micInUse = MicInUse::held,
    ).run()

    private companion object {
        const val PCM_SHARE_PACKAGE = "com.hisense.pcm.share"
        const val TIMEOUT_MILLIS = 1_500
    }
}
