package dev.lumbina.tv.core.domain.update

import kotlinx.coroutines.flow.Flow

/** A published version of Lumbina: where its package is, how big, and where its checksums are. */
data class Release(val version: String, val apkName: String, val apkUrl: String, val bytes: Long, val sumsUrl: String)

/** What asking for the latest release came to. */
sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck

    data class Available(val release: Release) : UpdateCheck

    /** No network, the server did not answer or would not, or the release lacks its package or checksums. */
    data object Failed : UpdateCheck
}

/** Where putting a release on the device is. */
sealed interface UpdateStep {
    /**
     * [fraction] of the package is on the device, 0 to 1. [needsPermission]: the device does not let
     * Lumbina install apps yet, and its screen for that is open or about to open.
     */
    data class Downloading(val fraction: Float, val needsPermission: Boolean = false) : UpdateStep

    /**
     * Downloaded and checked, but the device does not let Lumbina install apps yet: its screen for
     * that was opened, and the install goes on by itself once the user allows it and comes back.
     */
    data object NeedsPermission : UpdateStep

    /** Downloaded and checked; the system now asks the user, and the rest is the system's. */
    data object Installing : UpdateStep

    /**
     * Downloaded and checked; Lumbina is installing it over its own connection to the device's
     * debugging, with nothing asked: Lumbina stops and comes back as the new version.
     */
    data object InstallingItself : UpdateStep

    /** The user answered the system's question with "no": nothing is wrong, the release is still there. */
    data object Cancelled : UpdateStep

    /** The download did not get through. */
    data object Failed : UpdateStep

    /** What came is not what was published (its checksum differs), or not Lumbina's package. It was thrown away. */
    data object Corrupt : UpdateStep

    /** The system would not put it over this build: this one is signed with another key. */
    data object Refused : UpdateStep

    /** The published version is older than this build (its version code is lower): nothing was installed. */
    data object Older : UpdateStep

    /** The package is fine but the system did not install it: no room for it, or a failure of its own. */
    data object NotInstalled : UpdateStep
}

/**
 * Lumbina updating itself, for the edition that is not installed from a store. It asks only when
 * the user does, and tells the server nothing about the device beyond the request itself.
 */
interface AppUpdates {
    /** False in an edition a store keeps up to date: then nothing here is offered. */
    val supported: Boolean

    suspend fun check(): UpdateCheck

    /**
     * Downloads [release], checks it against its published checksum and installs it. Where the
     * device does not let Lumbina install apps yet, it asks the user for that first and waits
     * ([UpdateStep.NeedsPermission]), then goes on by itself.
     */
    fun install(release: Release): Flow<UpdateStep>

    /** Opens the device's screen where Lumbina may be allowed to install apps, where it has one. */
    fun askToInstallApps()
}

/**
 * Whether the version [candidate] is newer than [current]. Both are dotted numbers, with or
 * without a leading "v" and whatever follows the numbers ("0.0.4-debug"); a part that is missing
 * counts as zero. Something that is no version at all is not newer.
 */
fun isNewer(candidate: String, current: String): Boolean {
    val new = versionParts(candidate) ?: return false
    val old = versionParts(current) ?: return false
    for (index in 0 until maxOf(new.size, old.size)) {
        val difference = new.getOrElse(index) { 0 } - old.getOrElse(index) { 0 }
        if (difference != 0) return difference > 0
    }
    return false
}

private fun versionParts(version: String): List<Int>? =
    Regex("""\d+(\.\d+)*""").find(version)?.value?.split('.')?.map { it.toIntOrNull() ?: return null }

/**
 * The SHA-256 published for [fileName] in a checksums file of `sha256sum`'s format (the sum, then
 * the name, a line per file); null if the file is not named there.
 */
fun publishedSum(sums: String, fileName: String): String? = sums.lineSequence()
    .map { it.trim().split(Regex("""\s+"""), limit = 2) }
    .firstOrNull { it.size == 2 && it[1].trimStart('*') == fileName }
    ?.first()?.lowercase()

/** What the device's package manager said to a package sent to it over the shell. */
enum class ShellInstall {
    Done,

    /** It will not put this package over the installed one: another signing key, or an older version. */
    Refused,

    /** No answer (no connection), or a failure that another way of installing may get past. */
    Failed,

    /** Still installing when the shell stopped waiting: another way of installing would race with it. */
    Pending,
}

/**
 * The shell command that installs a package of [bytes] bytes sent to its input, in [dir], and
 * then opens [activity] (`package/.Activity`). The install stops the app it replaces and, with
 * it, this shell; so a shell of its own (setsid) installs and opens the app. This one prints a dot
 * a second while it waits (silence would read as a lost connection), then what the package
 * manager said, which an app still alive (the install refused) reads.
 */
fun shellInstallCommand(bytes: Long, activity: String, dir: String = "/data/local/tmp"): String {
    // Named by this shell's process: a shell left from an earlier try deletes only its own files.
    val names = "f=$dir/lumbina-update-\$\$.apk; o=\$f.out; "
    val received = "head -c $bytes > \$f && [ \"\$(stat -c %s \$f)\" = $bytes ] || { rm -f \$f; exit 1; }; "
    val installAndOpen = "cmd package install -r -S $bytes < \$f > \$o 2>&1; rm -f \$f; " +
        "grep -q Success \$o && am start -n $activity; sleep $ANSWER_KEPT_SECONDS; rm -f \$o"
    val wait = "n=0; while [ -e \$f ] && [ \$n -lt $WAIT_SECONDS ]; do echo .; sleep 1; n=\$((n + 1)); done; "
    return names + received +
        "setsid sh -c \"$installAndOpen\" < /dev/null > /dev/null 2>&1 & " +
        wait + "if [ -e \$f ]; then echo $PENDING; else cat \$o; fi"
}

/** How long the package manager's answer is kept for the shell waiting for it. */
private const val ANSWER_KEPT_SECONDS = 60

/** How long the shell waits for the install before it says it is still going. */
private const val WAIT_SECONDS = 600

private const val PENDING = "Pending"

/** Reads what [shellInstallCommand] printed: [answer] is null when the shell was not reached. */
fun shellInstallResult(answer: String?): ShellInstall = when {
    answer == null -> ShellInstall.Failed
    "Success" in answer -> ShellInstall.Done
    REFUSALS.any { it in answer } -> ShellInstall.Refused
    answer.lines().any { it == PENDING } -> ShellInstall.Pending
    else -> ShellInstall.Failed
}

/** [shellInstallCommand]'s output without its waiting dots. */
fun shellInstallAnswer(output: String): String = output.lines().filter { it != "." }.joinToString(" ").trim()

private val REFUSALS = listOf("INSTALL_FAILED_UPDATE_INCOMPATIBLE", "INSTALL_FAILED_VERSION_DOWNGRADE")
