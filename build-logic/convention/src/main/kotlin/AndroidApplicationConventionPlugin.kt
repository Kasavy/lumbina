import com.android.build.api.dsl.ApplicationExtension
import dev.lumbina.buildlogic.configureAndroid
import dev.lumbina.buildlogic.configureSigning
import dev.lumbina.buildlogic.libs
import dev.lumbina.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("lumbina.quality")

        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            configureSigning(this)
        }
    }
}
