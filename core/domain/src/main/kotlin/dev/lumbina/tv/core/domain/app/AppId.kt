package dev.lumbina.tv.core.domain.app

/** Identity of a launchable app entry: a package plus the activity to start. */
data class AppId(val packageName: String, val activityName: String) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(activityName.isNotBlank()) { "activityName must not be blank" }
    }

    /** Short form used by `adb` and component names, e.g. `org.pkg/.MainActivity`. */
    fun flatten(): String {
        val shortActivity = if (activityName.startsWith("$packageName.")) {
            activityName.removePrefix(packageName)
        } else {
            activityName
        }
        return "$packageName/$shortActivity"
    }
}
