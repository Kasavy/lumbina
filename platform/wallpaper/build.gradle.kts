plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.hilt)
}

android {
    // The Projectivy wallpaper plugin API (src/main/aidl).
    buildFeatures { aidl = true }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(projects.core.testing)
}
