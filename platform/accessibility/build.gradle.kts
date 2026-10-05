plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.hilt)
}

android {
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    testImplementation(projects.core.testing)
}
