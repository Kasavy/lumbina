import com.android.build.api.dsl.LibraryExtension
import dev.lumbina.buildlogic.configureAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android library module. AGP 9 compiles Kotlin itself, so no separate Kotlin plugin is applied. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("lumbina.quality")

        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            // Namespace follows the module path: :platform:packages -> dev.lumbina.tv.platform.packages
            namespace = "dev.lumbina.tv" + path.replace(':', '.').replace('-', '_')
        }
    }
}
