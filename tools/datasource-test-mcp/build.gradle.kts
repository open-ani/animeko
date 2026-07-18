/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.jetbrains.compose)
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.client)
    implementation(projects.datasource.datasourceApi)
    implementation(projects.datasource.dmhy)
    implementation(projects.datasource.mikan)
    implementation(projects.datasource.jellyfin)
    implementation(projects.datasource.ikaros)
    implementation(projects.app.shared.appData)
    implementation(projects.app.shared.appPlatform)
    implementation(projects.utils.ktorClient)
    implementation(projects.utils.logging)
    implementation(projects.utils.serialization)
    implementation(projects.utils.xml)
    implementation(projects.utils.jsonpath)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    // probe_video: 用与桌面 App 相同的 VLC 播放器真实播放视频
    implementation(libs.mediamp.api)
    implementation(libs.mediamp.vlc)
    implementation(libs.mediamp.mpv)
    implementation(libs.vlcj)
    implementation(compose.desktop.currentOs)

    runtimeOnly(libs.slf4j.simple)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit5.jupiter.api)
    testImplementation(libs.junit5.jupiter.params)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "me.him188.ani.tools.datasourcetestmcp.MainKt"
}
