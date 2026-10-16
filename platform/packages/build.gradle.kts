plugins {
    alias(libs.plugins.lumbina.android.library)
    alias(libs.plugins.lumbina.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(projects.core.testing)
}

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Where Lumbina looks for its updates (owner/repository on GitHub): a fork signed with its
        // own key sets its own in gradle.properties, or it would be offered ours, which can't install.
        val repo = providers.gradleProperty("lumbina.updatesRepo").get()
        // To test updating without publishing: a server of one's own that answers as GitHub's API does.
        val latest = providers.gradleProperty("lumbina.updatesUrl")
            .orElse("https://api.github.com/repos/$repo/releases/latest").get()
        buildConfigField("String", "UPDATES_LATEST", "\"$latest\"")
    }
}
