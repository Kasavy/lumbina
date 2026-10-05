package dev.lumbina.buildlogic

import com.android.build.api.dsl.CommonExtension
import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** SDK levels, Java level and unit-test setup shared by every Android module. */
fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = libs.version("compileSdk").toInt()
    android.defaultConfig.minSdk = libs.version("minSdk").toInt()
    android.compileOptions.sourceCompatibility = JAVA_VERSION
    android.compileOptions.targetCompatibility = JAVA_VERSION
    // Robolectric needs merged resources; unmocked android.* calls return defaults instead of throwing.
    android.testOptions.unitTests.isIncludeAndroidResources = true
    android.testOptions.unitTests.isReturnDefaultValues = true
    android.lint.warningsAsErrors = false
    android.lint.abortOnError = true
    android.lint.checkDependencies = true
    android.defaultConfig.manifestPlaceholders.putAll(processNames())

    configureKotlinCompiler()

    // Hilt/Robolectric generate unit-test sources even in modules without tests; only there
    // is "no tests discovered" expected. Modules with a src/test dir keep Gradle's safeguard.
    if (!file("src/test").exists()) {
        tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
    }

    dependencies {
        add("implementation", libs.lib("kotlinx-coroutines-android"))
        add("testImplementation", libs.lib("junit4"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("robolectric"))
    }
}

/**
 * The names of Lumbina's processes, which the manifests take as placeholders: ":ui", ":remote",
 * ":voice", ":provider". A build may put a prefix before them: `lumbina.processPrefix` in
 * local.properties or LUMBINA_PROCESS_PREFIX in the environment. Without one the names are plain.
 */
fun Project.processNames(): Map<String, String> {
    val local = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
    val prefix = local.getProperty("lumbina.processPrefix") ?: System.getenv("LUMBINA_PROCESS_PREFIX") ?: ""
    return listOf("ui", "remote", "voice", "provider").associate { "${it}Process" to ":$prefix$it" }
}
