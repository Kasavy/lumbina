import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.lumbina.buildlogic.lib
import dev.lumbina.buildlogic.libs
import dev.lumbina.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** detekt (with Compose rules) and ktlint formatting via Spotless, for every module. */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")
        pluginManager.apply("com.diffplug.spotless")

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig.set(true)
            parallel.set(true)
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            basePath.set(rootProject.layout.projectDirectory)
        }
        dependencies {
            add("detektPlugins", libs.lib("detekt-compose-rules"))
        }
        // One module at a time: every detekt task of a build shares one class loader, and two that
        // start at once race in its IntelliJ core ("Could not initialize class ...PluginEnabler").
        val detektLock = gradle.sharedServices.registerIfAbsent("detektLock", DetektLock::class.java) {
            maxParallelUsages.set(1)
        }
        tasks.withType<Detekt>().configureEach { usesService(detektLock) }

        // Passed explicitly: Spotless's ktlint step does not pick these up from .editorconfig.
        val ktlintRules = mapOf(
            "ktlint_code_style" to "android_studio",
            "max_line_length" to "120",
            "ij_kotlin_allow_trailing_comma" to "true",
            "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
        )
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("src/**/*.kt")
                ktlint(libs.version("ktlint")).editorConfigOverride(ktlintRules)
            }
            kotlinGradle {
                target("*.gradle.kts")
                ktlint(libs.version("ktlint")).editorConfigOverride(ktlintRules)
            }
        }
    }
}

/** Held by a running detekt task, see [QualityConventionPlugin]. */
abstract class DetektLock : BuildService<BuildServiceParameters.None>
