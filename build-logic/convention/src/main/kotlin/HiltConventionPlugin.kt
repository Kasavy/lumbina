import dev.lumbina.buildlogic.lib
import dev.lumbina.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt with KSP for Android modules. Pure JVM modules use plain javax.inject instead.
 * Modules with Hilt tests add `hilt-android-testing` + `kspTest(hilt-compiler)` themselves;
 * adding them everywhere makes KSP generate test sources in modules that have no tests.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}
