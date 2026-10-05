plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.lumbina.quality)
}

// Runs on a connected device against :app: generates the baseline profile shipped in release
// builds (./gradlew :app:generateBaselineProfile) and measures startup and scrolling
// (./gradlew :baselineprofile:connectedFossBenchmarkReleaseAndroidTest).
android {
    namespace = "dev.lumbina.tv.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Baseline profiles can be generated without root from API 33; benchmarks need 28.
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    flavorDimensions += "services"
    productFlavors {
        create("foss") { dimension = "services" }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
