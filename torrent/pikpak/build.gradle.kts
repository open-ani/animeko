/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

// Propagate PIKPAK_* vars from the repo-root .env file, and pikpak-* keys from local.properties,
// into JVM test tasks so PikPakTorrentEngineLiveTest can talk to the live service.
// Lines may use `KEY=value` or `KEY = value`; comment lines (#) and blanks are ignored.
tasks.withType<Test>().configureEach {
    fun readKeyValues(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return file.readLines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            val eq = line.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim().trim('"').trim('\'')
            key to value
        }.toMap()
    }

    readKeyValues(rootProject.file(".env"))
        .filterKeys { it.startsWith("PIKPAK_") }
        .forEach { (key, value) -> environment(key, value) }
    readKeyValues(rootProject.file("local.properties"))
        .filterKeys { it.startsWith("pikpak-") }
        .forEach { (key, value) ->
            environment("PIKPAK_" + key.removePrefix("pikpak-").replace('-', '_').uppercase(), value)
        }
}

kotlin {
    android {
        namespace = "me.him188.ani.torrent.pikpak"
    }
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.coroutines.core)
        api(libs.kotlinx.datetime)
        api(projects.torrent.torrentApi)
        api(projects.utils.platform)
        api(projects.utils.coroutines)
        api(projects.utils.io)
        api(projects.utils.ktorClient)
        api(projects.utils.logging)
        implementation(libs.atomicfu)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)

        // Auth, captcha, rate limiting, OSS signing, GCID etc. live in the SDK — this module only
        // supplies the torrent-engine layer on top. See https://github.com/NihilDigit/pikpak-kotlin.
        api("io.github.nihildigit:pikpak-kotlin:0.6.6")
    }
    sourceSets.getByName("desktopTest").dependencies {
        implementation(libs.ktor.client.mock)
    }
}
