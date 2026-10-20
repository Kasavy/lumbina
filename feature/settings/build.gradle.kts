plugins {
    alias(libs.plugins.lumbina.android.feature)
}

dependencies {
    // Asks for the TV listings permission from the channels page.
    implementation(libs.androidx.activity.compose)
    // A video's picture in the media library: a frame a second in, past a fade from black.
    implementation(libs.coil.video)
}
