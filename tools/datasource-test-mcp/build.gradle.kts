/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("ani.jvm-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.plugin.compose)
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
    implementation(libs.mediamp.mpv)
    // Keep the Compose desktop application model out of this CLI module: it also owns a `run`
    // task, which conflicts with the Gradle application plugin used to build the launcher.
    implementation(
        "org.jetbrains.compose.desktop:desktop-jvm-${getOsTriple()}:${libs.versions.compose.multiplatform.get()}",
    )

    implementation(libs.mediamp.ffmpeg.desktop)

    when (val triple = getOsTriple()) {
        "windows-x64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)
        "linux-x64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.linux.x64)
        "macos-x64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.macos.x64)
        "macos-arm64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.macos.arm64)
        "windows-arm64" -> runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.arm64)
        else -> throw UnsupportedOperationException("Unknown os: $triple")
    }

    if (getLocalProperty("ani.build.mediamp.path") != null) {
        runtimeOnly(libs.mediamp.mpv) {
            capabilities {
                requireCapability("org.openani.mediamp:mediamp-mpv-runtime-${getOsTriple()}")
            }
        }
    } else {
        when (val triple = getOsTriple()) {
            "windows-x64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
            "windows-arm64" -> runtimeOnly(libs.mediamp.mpv.runtime.windows.arm64)
            "linux-x64" -> runtimeOnly(libs.mediamp.mpv.runtime.linux.x64)
            "macos-arm64" -> runtimeOnly(libs.mediamp.mpv.runtime.macos.arm64)
            else -> {}
        }
    }

    runtimeOnly(libs.slf4j.simple)
    // 验证码浏览器要在 Swing EDT (CEF context) 上取 UA, WebSessionManager 用 Dispatchers.Main 切过去
    runtimeOnly(libs.kotlinx.coroutines.swing)

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

// 发行包 lib/ 下依赖 jar 的文件名. Compose Multiplatform 的部分 org.jetbrains.* 构件是不含 class 的空壳,
// 文件名与对应的 androidx.* 构件相同 (如 runtime-desktop-1.12.1.jar), 只按文件名平铺会互相覆盖,
// 所以外部模块的 jar 加上 group 前缀.
val distLibNames: Provider<Map<File, String>> = configurations.runtimeClasspath
    .flatMap { it.incoming.artifacts.resolvedArtifacts }
    .map { artifacts ->
        artifacts.associate { artifact ->
            val id = artifact.id.componentIdentifier
            val fileName = artifact.file.name
            artifact.file to if (id is ModuleComponentIdentifier) "${id.group}-$fileName" else fileName
        }
    }

// cmd.exe 单行命令上限 8191 字符, 启动脚本逐个列出几百个依赖 jar 会超长.
// 启动脚本只引用这个 jar, 依赖由它的 manifest Class-Path 列出 (相对 lib/ 解析).
//
// Class-Path 保持 runtimeClasspath 的顺序, 只把 mpv runtime 提到最前: mpv 和 ffmpeg 两个 runtime jar
// 根目录有同名但不同构建的 libav*, 没有指定 -Dani.mpv.native.dir 时 mpv 从 classpath 首个匹配的 jar 提取原生库,
// 需要命中自己 jar 里的全功能版本; 而 ffmpeg runtime 经 :utils:http-downloader 传递进来, 默认排在前面.
val classpathJar = tasks.register<Jar>("classpathJar") {
    archiveFileName = "datasource-test-mcp-classpath.jar"
    destinationDirectory = layout.buildDirectory.dir("classpath-jar")
    val libNames = distLibNames.map { names ->
        val (mpvRuntime, others) = names.values.partition { "mediamp-mpv-runtime-" in it }
        mpvRuntime + others
    }
    inputs.property("libNames", libNames)
    manifest.attributes("Class-Path" to libNames.map { it.joinToString(" ") })
}

tasks.startScripts {
    classpath = files(tasks.jar, classpathJar)
    // 发行包可在 jbr/ 自带 JetBrains Runtime: WebView 解析需要带 JCEF 的 JBR, 普通 JDK 能启动但解析不了视频页,
    // 所以自带的 JBR 优先于 JAVA_HOME.
    doLast {
        fun File.insertBefore(anchor: String, text: String) {
            val script = readText()
            check(anchor in script) { "启动脚本模板里找不到 '$anchor': $this" }
            writeText(script.replaceFirst(anchor, text + anchor))
        }
        val task = this as CreateStartScripts
        task.unixScript.insertBefore(
            "# Determine the Java command to use to start the JVM.",
            "if [ -x \"\$APP_HOME/jbr/bin/java\" ] ; then\n    JAVA_HOME=\$APP_HOME/jbr\nfi\n\n",
        )
        task.windowsScript.insertBefore(
            "@rem Find java.exe",
            "if exist \"%APP_HOME%\\jbr\\bin\\java.exe\" set JAVA_HOME=%APP_HOME%\\jbr\r\n\r\n",
        )
    }
}

distributions.main {
    contents {
        into("lib") {
            from(classpathJar)
        }
        val libNames = distLibNames
        eachFile {
            libNames.get()[file]?.let { name = it }
        }
    }
}
