plugins {
    id("ani.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android { namespace = "me.him188.ani.remote.settings" }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.json)
        api(libs.ktor.client.core)
    }
}
