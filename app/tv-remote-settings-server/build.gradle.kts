plugins {
    id("ani.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "me.him188.ani.tv.remotesettings"
        packaging {
            resources {
                pickFirsts.add("META-INF/AL2.0")
                pickFirsts.add("META-INF/LGPL2.1")
                excludes.add("META-INF/DEPENDENCIES")
                excludes.add("META-INF/licenses/ASM")
                excludes.add("win32-x86-64/attach_hotspot_windows.dll")
                excludes.add("win32-x86/attach_hotspot_windows.dll")
            }
        }
    }
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
