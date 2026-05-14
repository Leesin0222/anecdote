import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AnecdoteAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("jacoco")
            pluginManager.apply("anecdote.quality")
            // Kotlin plugin is applied via each module's own plugins block
            // (avoids "kotlin extension already registered" conflicts with AGP 9.x)

            extensions.configure<LibraryExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 24
                    consumerProguardFiles("consumer-rules.pro")
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                buildTypes {
                    getByName("debug") {
                        enableUnitTestCoverage = true
                    }
                }
                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }

            extensions.configure<JacocoPluginExtension> {
                toolVersion = "0.8.12"
            }

            tasks.register<JacocoReport>("jacocoTestReport") {
                dependsOn("testDebugUnitTest")
                group = "verification"
                description = "Generates JaCoCo coverage report from debug unit tests."

                reports {
                    xml.required.set(true)
                    html.required.set(true)
                    csv.required.set(false)
                }

                val excludes =
                    listOf(
                        "**/R.class",
                        "**/R\$*.class",
                        "**/BuildConfig.*",
                        "**/Manifest*.*",
                        "**/*Test*.*",
                        "android/**/*.*",
                        "**/*\$Lambda\$*.*",
                        "**/*\$inlined\$*.*",
                    )

                val buildDirFile = layout.buildDirectory.get().asFile
                val javaClasses = fileTree("$buildDirFile/intermediates/javac/debug") { exclude(excludes) }
                val kotlinClasses = fileTree("$buildDirFile/tmp/kotlin-classes/debug") { exclude(excludes) }

                classDirectories.setFrom(javaClasses, kotlinClasses)
                sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
                executionData.setFrom(
                    fileTree(buildDirFile) {
                        include(
                            "jacoco/testDebugUnitTest.exec",
                            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                            "outputs/unit_test_code_coverage/**/*.exec",
                        )
                    },
                )
            }
        }
    }
}
