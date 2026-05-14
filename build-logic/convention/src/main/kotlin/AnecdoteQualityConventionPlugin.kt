import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

class AnecdoteQualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            // Wire detekt-formatting ruleset (ports ktlint rules into detekt) so .kt
            // sources still get ktlint-style enforcement. The ktlint Gradle plugin
            // itself only handles .kts files under AGP 9.x because the built-in Kotlin
            // support doesn't expose a KGP source set for ktlint to discover.
            val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
            catalog.findLibrary("detekt-formatting").ifPresent { provider ->
                dependencies.add("detektPlugins", provider.get())
            }

            extensions.configure<KtlintExtension> {
                android.set(true)
                ignoreFailures.set(false)
                reporters {
                    reporter(ReporterType.PLAIN)
                    reporter(ReporterType.HTML)
                }
                filter {
                    exclude { it.file.path.contains("/build/") }
                }
            }

            // Toggle auto-correct via `./gradlew detekt -Pdetekt.autoCorrect=true`.
            // In CI we want it strictly off; during local cleanup it's invaluable.
            val autoCorrectEnabled =
                providers.gradleProperty("detekt.autoCorrect")
                    .map { it.toBoolean() }
                    .getOrElse(false)

            extensions.configure<DetektExtension> {
                config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                autoCorrect = autoCorrectEnabled
                source.setFrom(
                    files("src/main/java", "src/main/kotlin", "src/test/java", "src/test/kotlin"),
                )
            }

            tasks.withType<Detekt>().configureEach {
                jvmTarget = "11"
                reports {
                    html.required.set(true)
                    xml.required.set(true)
                    txt.required.set(false)
                    sarif.required.set(false)
                    md.required.set(false)
                }
            }
        }
    }
}
