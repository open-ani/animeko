plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `ani-mpp-lib-targets`
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.torrent.offline"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
    }
}
