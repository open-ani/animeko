/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    id("ani.jvm-library")
}

// macOS 系统分享菜单 (NSSharingServicePicker) 的 JNI 实现, 由 app-platform 的 MacosShareSheet 调用.
//
// src/main/objc 里的 Objective-C++ 只在 macOS 主机上编译: 用 Xcode 的 clang 编成同时含 arm64 与 x86_64 的 dylib,
// 作为资源装进本模块的 jar. 打包时 app/desktop 的 unpackComposeDesktopNativeLibraries 把它从 jar 里解到运行库目录
// (与 onnxruntime、mediamp 的原生库一样), 开发时 (gradlew run、测试) MacosShareSheetNative 从 classpath 解到临时目录加载.
// 其他主机上本模块只产出不含 dylib 的 jar, MacosShareSheetNative.isAvailable 为 false, 分享退回到在 Finder 中定位文件.
val isMacosHost = getOs() == Os.MacOS

abstract class CompileMacosShareNative @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: RegularFileProperty

    /** 提供 jni.h 的 JDK. */
    @get:Input
    abstract val javaHome: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        val javaHomeDir = File(javaHome.get())
        val outputFile = output.get().asFile
        outputFile.parentFile.mkdirs()
        execOperations.exec {
            commandLine(
                "xcrun", "clang++",
                "-std=c++17", "-fobjc-arc", "-O2", "-Wall",
                "-dynamiclib", "-arch", "arm64", "-arch", "x86_64", "-mmacosx-version-min=11.0",
                "-I", javaHomeDir.resolve("include").absolutePath,
                "-I", javaHomeDir.resolve("include/darwin").absolutePath,
                "-framework", "AppKit", "-framework", "Foundation",
                "-o", outputFile.absolutePath,
                source.get().asFile.absolutePath,
            )
        }
    }
}

if (isMacosHost) {
    val compileMacosShareNative = tasks.register<CompileMacosShareNative>("compileMacosShareNative") {
        description = "Builds libanimeko_macos_share.dylib (arm64 + x86_64) from src/main/objc"
        source = layout.projectDirectory.file("src/main/objc/animeko_macos_share.mm")
        javaHome = providers.environmentVariable("JAVA_HOME")
            .orElse(providers.systemProperty("java.home"))
        output = layout.buildDirectory.file("native/libanimeko_macos_share.dylib")
    }
    // 放在资源根目录, 与 MacosShareSheetNative 查找的名字一致
    tasks.processResources {
        from(compileMacosShareNative.flatMap { it.output })
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

// CI 的桌面测试步骤跑的是 desktopTest; 这是纯 JVM 模块, 用别名让加载测试在 macOS 的 CI 上也跑到
tasks.register("desktopTest") {
    group = "verification"
    dependsOn(tasks.test)
}
