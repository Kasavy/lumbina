plugins {
    alias(libs.plugins.lumbina.android.feature)
}

dependencies {
    // Asks for the TV listings permission from the channels page.
    implementation(libs.androidx.activity.compose)
}
