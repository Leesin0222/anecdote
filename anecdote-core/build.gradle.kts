plugins {
    id("anecdote.android.library")
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
