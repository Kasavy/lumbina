plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    // Program posters, channel logos and wallpapers (http, content, file and resource URIs).
    api(libs.coil.compose)
    // QR code for the phone remote address.
    implementation(libs.zxing.core)
    // Video wallpapers.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
}
