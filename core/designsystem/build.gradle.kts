plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.android.compose)
}

dependencies {
    // Accent and text size come from the home appearance settings.
    implementation(projects.core.domain)
}
