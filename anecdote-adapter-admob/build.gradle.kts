plugins {
    id("anecdote.android.library")
    id("anecdote.android.publish")
}

android {
    namespace = "com.yongjincompany.anecdote.adapter.admob"
}

dependencies {
    api(project(":anecdote-core"))
    implementation(libs.play.services.ads)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
