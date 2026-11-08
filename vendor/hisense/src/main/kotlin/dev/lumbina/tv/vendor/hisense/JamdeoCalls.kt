package dev.lumbina.tv.vendor.hisense

import dalvik.system.PathClassLoader

/**
 * The call numbers of the firmware's input and system services. They differ between firmware
 * versions (a C2U's are shifted from a C3 Ultra's: its call 11 answered with the inputs), so they are
 * read by name from the firmware's own interfaces, the AIDL stubs in com.jamdeo.flow.jar; a number
 * that can't be read is the C3 Ultra's. [fromFirmware]: every one was read.
 */
internal data class JamdeoCalls(
    val setCurrent: Int,
    val getCurrent: Int,
    val getName: Int,
    val getInputs: Int,
    val getConnected: Int,
    val invokeTvMethod: Int,
    val fromFirmware: Boolean,
) {
    /** For a report. */
    fun said(): String =
        "set $setCurrent, current $getCurrent, name $getName, inputs $getInputs, connected $getConnected, " +
            "tv method $invokeTvMethod (${if (fromFirmware) "from the firmware" else "some as on a C3 Ultra"})"
}

/** The numbers by [lookup] (an interface's simple name and a method: its call number, or null). */
internal fun jamdeoCalls(lookup: (service: String, method: String) -> Int?): JamdeoCalls {
    var missing = false
    fun code(service: String, method: String, known: Int) = lookup(service, method) ?: known.also { missing = true }
    return JamdeoCalls(
        setCurrent = code(SOURCE_SERVICE_STUB, "setCurrentInputSource", known = 1),
        getCurrent = code(SOURCE_SERVICE_STUB, "getCurrentInputSource", known = 4),
        getName = code(SOURCE_SERVICE_STUB, "getInputSourceName", known = 11),
        getInputs = code(SOURCE_SERVICE_STUB, "getInputSourceArray", known = 12),
        // Spelt so by the firmware.
        getConnected = code(SOURCE_SERVICE_STUB, "getConectedSourcesArray", known = 37),
        invokeTvMethod = code(SYSTEM_SERVICE_STUB, "invokeTvMethod", known = 57),
        // Read after the others: they are evaluated in order.
        fromFirmware = !missing,
    )
}

/** Reads a stub's TRANSACTION_ constants from the firmware's jar; null for each it can't. */
internal fun firmwareCalls(): JamdeoCalls {
    val loader = runCatching { PathClassLoader(FLOW_JAR, JamdeoCalls::class.java.classLoader) }.getOrNull()
    return jamdeoCalls { service, method ->
        loader?.let {
            runCatching {
                Class.forName("$PACKAGE.$service\$Stub", false, it).getDeclaredField("TRANSACTION_$method")
                    .apply { isAccessible = true }
                    .getInt(null)
            }.getOrNull()
        }
    }
}

private const val FLOW_JAR = "/system/framework/com.jamdeo.flow.jar"
private const val PACKAGE = "com.jamdeo.tv.service"
internal const val SOURCE_SERVICE_STUB = "ISourceRemoteService"
internal const val SYSTEM_SERVICE_STUB = "ISystemRemoteService"
