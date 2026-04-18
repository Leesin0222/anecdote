// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val anecdoteVersion: String by extra("0.1.1")

subprojects {
    if (name.startsWith("anecdote-")) {
        version = anecdoteVersion
    }
}

tasks.register<Copy>("collectAars") {
    group = "distribution"
    description = "Collect release AARs for all anecdote-* modules into build/artifacts/ with versioned names."

    val distributableModules = subprojects.filter { it.name.startsWith("anecdote-") }

    distributableModules.forEach { module ->
        dependsOn("${module.path}:assembleRelease")
        from(module.layout.buildDirectory.dir("outputs/aar")) {
            include("${module.name}-release.aar")
            rename { "${module.name}-$anecdoteVersion.aar" }
        }
    }

    into(layout.buildDirectory.dir("artifacts"))

    doLast {
        val artifactDir = layout.buildDirectory.dir("artifacts").get().asFile
        logger.lifecycle("AARs collected into ${artifactDir.absolutePath}")
        artifactDir.listFiles()?.sorted()?.forEach { f -> logger.lifecycle("  - ${f.name}") }
    }
}
