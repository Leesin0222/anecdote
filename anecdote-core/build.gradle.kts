plugins {
    id("anecdote.android.library")
    id("anecdote.android.publish")
}

android {
    namespace = "com.yongjincompany.anecdote"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
