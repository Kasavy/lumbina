plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

/*
 * Module dependency rules (see docs/architecture.md):
 *  - core:domain / core:common: no Android, depend on nothing but core:common.
 *  - feature:*: core:* only.
 *  - core:network: core:common only (plain JVM HTTP shared by platform adapters).
 *  - platform:*: core:domain, core:common and core:network only.
 *  - speech:*: core:domain and core:common only.
 *  - vendor:*: core:* plus platform:accessibility (key mapping types).
 *  - app: anything.
 *  - companion:*: standalone APKs installed next to Lumbina (e.g. the TV provider), no project dependencies.
 *  - baselineprofile: drives :app from outside (its targetProjectPath), nothing else.
 */
val allowedDependencies: Map<String, (String) -> Boolean> = mapOf(
    ":core:domain" to { dep -> dep == ":core:common" },
    ":core:common" to { _ -> false },
    ":core:network" to { dep -> dep == ":core:common" },
    ":core:" to { dep -> dep.startsWith(":core:") },
    ":feature:" to { dep -> dep.startsWith(":core:") },
    ":platform:" to { dep -> dep in setOf(":core:domain", ":core:common", ":core:network") },
    ":speech:" to { dep -> dep == ":core:domain" || dep == ":core:common" },
    ":vendor:" to { dep -> dep.startsWith(":core:") || dep == ":platform:accessibility" },
    ":app" to { _ -> true },
    ":companion:" to { _ -> false },
    ":baselineprofile" to { dep -> dep == ":app" },
)

// Test-only edges to :core:testing are always allowed and are not listed here.
val moduleEdges: Provider<List<String>> = provider {
    subprojects.flatMap { module ->
        module.configurations
            .filter { !it.name.startsWith("detekt") }
            .flatMap { config ->
                val isTestConfig = config.name.contains("test", ignoreCase = true)
                config.dependencies.withType<ProjectDependency>()
                    .filterNot { isTestConfig && it.path == ":core:testing" }
                    .map { "${module.path}->${it.path}" }
            }
    }.distinct()
}

tasks.register("checkModuleGraph") {
    group = "verification"
    description = "Fails if a module depends on a module its layer may not use."
    val edges = moduleEdges
    val rules = allowedDependencies
    doLast {
        val violations = edges.get().mapNotNull { edge ->
            val (from, to) = edge.split("->")
            if (from == to) return@mapNotNull null
            val rule = rules.entries.firstOrNull { (prefix, _) -> from == prefix || from.startsWith(prefix) }
                ?: return@mapNotNull "$from has no dependency rule"
            if (rule.value(to)) null else "$from must not depend on $to"
        }
        check(violations.isEmpty()) { "Module graph violations:\n" + violations.joinToString("\n") { "  - $it" } }
    }
}
