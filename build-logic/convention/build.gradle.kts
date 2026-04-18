plugins {
    `kotlin-dsl`
}

group = "com.yongjincompany.anecdote.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("anecdoteAndroidLibrary") {
            id = "anecdote.android.library"
            implementationClass = "AnecdoteAndroidLibraryConventionPlugin"
        }
        register("anecdoteAndroidApplication") {
            id = "anecdote.android.application"
            implementationClass = "AnecdoteAndroidApplicationConventionPlugin"
        }
    }
}
