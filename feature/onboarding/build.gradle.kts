plugins {
    alias(libs.plugins.lumbina.android.feature)
}

dependencies {
    // Asks for the TV listings permission from the setup.
    implementation(libs.androidx.activity.compose)
}
