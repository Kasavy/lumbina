import dev.lumbina.buildlogic.lib
import dev.lumbina.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Feature module: UI + view models. Depends on core modules only; adapters
 * (platform, vendor, speech) reach it through domain ports.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("lumbina.android.library")
        pluginManager.apply("lumbina.android.compose")
        pluginManager.apply("lumbina.hilt")

        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-hilt-navigation-compose"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
