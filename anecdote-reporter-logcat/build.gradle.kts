plugins {
    id("anecdote.android.library")
}

android {
    namespace = "com.yongjincompany.anecdote.reporter.logcat"
}

dependencies {
    api(project(":anecdote-core"))

    testImplementation(libs.junit)
}
