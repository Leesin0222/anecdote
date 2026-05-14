plugins {
    id("anecdote.android.library")
    id("anecdote.android.publish")
}

android {
    namespace = "com.yongjincompany.anecdote.reporter.firebase"
}

dependencies {
    api(project(":anecdote-core"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
