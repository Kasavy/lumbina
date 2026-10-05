plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.hilt)
}

android {
    // Between the app and the recognizer in the `:voice` process (src/main/aidl).
    buildFeatures { aidl = true }
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    // Pulls JNA as an AAR (native libjnidispatch for every ABI).
    implementation(libs.vosk.android)
    testImplementation(projects.core.testing)
}
