import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.extensions.DetektExtension
import dev.lumbina.buildlogic.lib
import dev.lumbina.buildlogic.libs
import dev.lumbina.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

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
