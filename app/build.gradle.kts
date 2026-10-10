plugins {
    alias(libs.plugins.lumbina.android.application)
    alias(libs.plugins.lumbina.android.compose)
    alias(libs.plugins.lumbina.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "dev.lumbina.tv"

    defaultConfig {
        applicationId = "dev.lumbina.tv"
        versionCode = 3
        versionName = "0.0.3"
    }

    flavorDimensions += "services"
    productFlavors {
        // A single flavor; a "gms" one can be added without touching core modules.
        create("foss") { dimension = "services" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// Lumbina carries the TV Provider companion in its assets and installs it over its own adb where
// the device has no TV provider (TvProviderCompanion). The companion is built as this variant is:
// debug with debug, anything else with release, so both carry the same key.
abstract class CopyCompanionApk : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apks: DirectoryProperty

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun copy() {
        val apk = apks.get().asFile.listFiles { file -> file.extension == "apk" }?.singleOrNull()
            ?: error("one companion APK expected in ${apks.get().asFile}")
        val target = assets.get().asFile.resolve("companion").apply { deleteRecursively() }
        apk.copyTo(target.resolve("tvprovider.apk"))
    }
}

// The license and the notices of others' work go into the APK itself (assets/legal): an APK is
// often passed on alone, and some of those licenses ask for their text next to the binary.
abstract class CopyLegalTexts : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val texts: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = assets.get().asFile.resolve("legal").apply { deleteRecursively() }
        texts.forEach { it.copyTo(target.resolve(it.name)) }
    }
}

androidComponents {
    onVariants { variant ->
        val legal = tasks.register<CopyLegalTexts>(
            "copyLegalTexts${variant.name.replaceFirstChar(Char::uppercase)}",
        ) {
            texts.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(legal, CopyLegalTexts::assets)
        val type = if (variant.buildType == "debug") "debug" else "release"
        val copy = tasks.register<CopyCompanionApk>(
            "copyCompanionApk${variant.name.replaceFirstChar(Char::uppercase)}",
        ) {
            dependsOn(":companion:tvprovider:assemble${type.replaceFirstChar(Char::uppercase)}")
            apks.set(rootProject.layout.projectDirectory.dir("companion/tvprovider/build/outputs/apk/$type"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copy, CopyCompanionApk::assets)
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)

    implementation(projects.feature.home)
    implementation(projects.feature.settings)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.screensaver)

    implementation(projects.platform.packages)
    implementation(projects.platform.accessibility)
    implementation(projects.platform.system)
    implementation(projects.platform.weather)
    implementation(projects.platform.wallpaper)
    implementation(projects.platform.tvprovider)
    implementation(projects.platform.adb)
    implementation(projects.speech.vosk)
    implementation(projects.speech.cloud)
    implementation(projects.vendor.generic)
    implementation(projects.vendor.hisense)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

    testImplementation(projects.core.testing)
    // Startup, home scrolling and settings, recorded on a device by :baselineprofile.
    baselineProfile(projects.baselineprofile)
}
