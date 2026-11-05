package dev.lumbina.tv.vendor.hisense

import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.DeviceDiagnostics
import kotlinx.coroutines.runBlocking

/**
 * What a report asks a Hisense firmware: mostly about the remote's mic, which is the part that
 * differs between its generations, and what its input service answers ([inputs]).
 */
internal class HisenseDiagnostics(private val inputs: HisenseInputs) : DeviceDiagnostics {

    /**
     * Whether HsPcmShare runs, and which voice packages are installed. Whether it listens the
     * probe finds out by connecting to its socket and port.
     */
    override val shellCommands = listOf(
        "dumpsys activity services ${PcmShareProtocol.PACKAGE}",
        "pm list packages | grep -i -E 'pcm|speech|voice|aiview|katniss'",
    )

    override fun probe(): List<DeviceAnswer> = PcmShareProbe(
        routes = listOf(
            "local socket" to { LocalSocketTransport.connect(readTimeoutMillis = TIMEOUT_MILLIS) },
            "port ${TcpTransport.PORT}" to { TcpTransport.connect(TIMEOUT_MILLIS) },
        ),
        micInUse = MicInUse::held,
    ).run() + runBlocking { inputs.answers() }

    private companion object {
        const val TIMEOUT_MILLIS = 1_500
    }
}
