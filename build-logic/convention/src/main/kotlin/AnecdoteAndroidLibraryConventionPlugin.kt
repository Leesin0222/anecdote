import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class AnecdoteAndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
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
                testOptions {
                    unitTests.isReturnDefaultValues = true
                }
            }

            tasks.withType(KotlinCompile::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
}
