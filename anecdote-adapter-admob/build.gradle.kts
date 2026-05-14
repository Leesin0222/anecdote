plugins {
    id("anecdote.android.library")
    id("anecdote.android.publish")
}

android {
    namespace = "com.yongjincompany.anecdote.adapter.admob"
}

dependencies {
    api(project(":anecdote-core"))
    // The host app is expected to provide its own Google Mobile Ads SDK
    // (com.google.android.gms:play-services-ads). Marking it compileOnly
    // here prevents version / variant clashes with the host's existing
    // ad stack — see README "Requirements for the AdMob adapter".
    compileOnly(libs.play.services.ads)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.play.services.ads)
}
