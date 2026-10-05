plugins {
    alias(libs.plugins.lumbina.jvm.library)
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.javax.inject)
}
