plugins {
    alias(libs.plugins.lumbina.android.application)
}

// Lumbina TV Provider: stands in for com.android.providers.tv on devices that lack it,
// so apps can publish channels, preview programs and "continue watching" for Lumbina to show.
// A separate APK: on a device with a real TV Provider it cannot be installed (same authority).
// Signed with the same key as Lumbina (configureSigning in build-logic), which is how it recognises
// Lumbina as the one reader of all rows.
android {
    namespace = "dev.lumbina.tv.tvprovider"

    defaultConfig {
        applicationId = "dev.lumbina.tv.tvprovider"
        versionCode = 1
        versionName = "0.1.0"
    }
}
