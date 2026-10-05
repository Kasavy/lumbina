plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
}

ksp {
    // Exported schemas are the reference for migration tests.
    arg("room.schemaLocation", "$projectDir/schemas")
}
