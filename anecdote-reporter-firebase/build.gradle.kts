plugins {
    id("anecdote.android.library")
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
