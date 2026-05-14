import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get

class AnecdotePublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("maven-publish")

        target.extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        target.afterEvaluate {
            val releaseComponent = target.components["release"]
            target.extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        groupId = "com.yongjincompany.anecdote"
                        artifactId = target.name
                        version = target.version.toString()
                        from(releaseComponent)
                        pom {
                            name.set(target.name)
                            description.set("anecdote SDK: ${target.name}")
                            url.set("https://github.com/Leesin0222/anecdote")
                            licenses {
                                license {
                                    name.set("Apache License, Version 2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                }
                            }
                        }
                    }
                }
                repositories {
                    maven {
                        name = "GitHubPackages"
                        url = target.uri("https://maven.pkg.github.com/Leesin0222/anecdote")
                        credentials {
                            username = (target.findProperty("gpr.user") as String?)
                                ?: System.getenv("GITHUB_ACTOR")
                            password = (target.findProperty("gpr.token") as String?)
                                ?: System.getenv("GITHUB_TOKEN")
                        }
                    }
                }
            }
        }
    }
}
