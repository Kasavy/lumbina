plugins {
    alias(libs.plugins.lumbina.jvm.library)
}

dependencies {
    implementation(libs.javax.inject)
    testImplementation(projects.core.testing)
}
