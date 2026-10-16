plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.hilt)
}

dependencies {
    implementation(projects.core.domain)
    testImplementation(projects.core.testing)
}
