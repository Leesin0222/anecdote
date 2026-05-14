plugins {
    id("anecdote.android.library")
    id("anecdote.android.publish")
}

android {
    namespace = "com.yongjincompany.anecdote.reporter.logcat"
}

dependencies {
    api(project(":anecdote-core"))

    testImplementation(libs.junit)
}
