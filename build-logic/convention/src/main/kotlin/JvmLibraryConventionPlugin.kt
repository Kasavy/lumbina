import dev.lumbina.buildlogic.JAVA_VERSION
import dev.lumbina.buildlogic.configureKotlinCompiler
import dev.lumbina.buildlogic.lib
import dev.lumbina.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Pure Kotlin/JVM module (no Android SDK): domain and shared utilities. Tests run on JUnit 5. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("lumbina.quality")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }
        configureKotlinCompiler()

        dependencies {
            add("implementation", libs.lib("kotlinx-coroutines-core"))
            add("testImplementation", platform(libs.lib("junit5-bom")))
            add("testImplementation", libs.lib("junit5-jupiter"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testRuntimeOnly", libs.lib("junit5-launcher"))
        }
        tasks.withType<Test>().configureEach { useJUnitPlatform() }
    }
}
