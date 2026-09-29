plugins {
    id("ani.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android { namespace = "me.him188.ani.tv.remotesettings" }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.remoteSettings)
    }
    sourceSets.getByName("jvmMain").dependencies {
        implementation(libs.ktor.server.core)
        implementation(libs.ktor.server.cio)
    }
    sourceSets.getByName("jvmTest").dependencies {
        implementation(libs.ktor.server.test.host)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
        implementation(libs.kotlinx.coroutines.test)
    }
}
