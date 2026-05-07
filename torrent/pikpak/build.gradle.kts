/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `ani-mpp-lib-targets`
    alias(libs.plugins.kotlin.plugin.serialization)
}

// Propagate PIKPAK_* vars from the repo-root .env file into JVM test tasks
// so PikPakLiveSmokeTest / CleanupProbeTest can talk to the live service.
// .env lines may use `KEY=value` or `KEY = value` (the latter matches what
// the user already wrote); comment lines (#) and blanks are ignored.
tasks.withType<Test>().configureEach {
    val dotenv = rootProject.file(".env")
    if (!dotenv.exists()) return@configureEach
    dotenv.readLines().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        val eq = line.indexOf('=')
        if (eq <= 0) return@forEach
        val key = line.substring(0, eq).trim()
        val value = line.substring(eq + 1).trim().trim('"').trim('\'')
        if (key.startsWith("PIKPAK_")) environment(key, value)
    }
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.torrent.pikpak"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.datetime)
        api(projects.utils.platform)
        api(projects.utils.coroutines)
        api(projects.utils.io)
        api(projects.utils.ktorClient)
        api(projects.utils.logging)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
        // Auth, captcha, rate limiting, OSS signing, GCID etc. live in the
        // SDK — this module only supplies the offline-task orchestration
        // layer on top. See https://github.com/NihilDigit/pikpak-kotlin.
        api("io.github.nihildigit:pikpak-kotlin:0.4.3")
    }
    sourceSets.commonTest.dependencies {
        // kotlin-test + kotlinx-coroutines-test come in transitively from
        // :utils:testing (injected by ani-mpp-lib-targets). Declaring
        // `kotlin("test")` here triggers JVM variant inference to
        // kotlin-test-junit (JUnit 4), which collides with the JUnit 5
        // variant the de.mannodermaus.android-junit5 plugin pulls onto
        // androidDeviceTestCompileClasspath — same capability, two modules.
    }
    sourceSets.getByName("desktopTest").dependencies {
        // Mock engine drives PikPakKtorAbiCompatTest, which forces the SDK's
        // Ktor companion-object accesses (HttpMethod.Post, ContentType.*, ...)
        // to resolve against animeko's pinned Ktor version. If the SDK was
        // built against an incompatible Ktor ABI, the first request path
        // throws IllegalAccessError at link time and the test fails.
        implementation(libs.ktor.client.mock)
    }
}
