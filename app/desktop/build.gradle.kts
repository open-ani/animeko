/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import com.android.utils.CpuArchitecture
import com.android.utils.osArchitecture
import com.google.gson.Gson
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.kotlin.cli.common.isWindows
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.util.UUID

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.atomicfu")
    idea
}

dependencies {
    implementation(projects.app.shared)
    implementation(projects.app.shared.uiFoundation)
    implementation(projects.app.shared.application)
    implementation(compose.components.resources)
    implementation(libs.log4j.core)
    implementation(libs.vlcj)
}

// workaround for compose limitation
tasks.named("processResources") {
    dependsOn(":app:shared:desktopProcessResources")
    dependsOn(":app:shared:ui-foundation:desktopProcessResources")
}

sourceSets {
    main {
        resources.srcDirs(
            projects.app.shared.dependencyProject.layout.buildDirectory
                .file("processedResources/desktop/main"),
            projects.app.shared.uiFoundation.dependencyProject.layout.buildDirectory
                .file("processedResources/desktop/main"),
        )
    }
}

compose.desktop {
    application {
        jvmArgs(
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=TRACE",
            "-Dsun.java2d.metal=true",
            "-Djogamp.debug.JNILibLoader=true", // JCEF 加载 native 库的日志, 方便 debug
            // JCEF
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "-Xmx128m",
        )
        if (getOs() == Os.MacOS) {
            jvmArgs(
                "--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED",
            )
        }
        mainClass = "me.him188.ani.app.desktop.AniDesktop"
        nativeDistributions {
            modules(
                "jdk.unsupported", // sun.misc.Unsafe used by androidx datastore
                "java.management", // javax.management.MBeanRegistrationException
                "java.net.http",
                "jcef",
                "gluegen.rt",
                "jogl.all",
            )

            // ./gradlew suggestRuntimeModules

            appResourcesRootDir.set(file("appResources"))
            targetFormats(
                *buildList {
                    add(TargetFormat.Deb)
                    add(TargetFormat.Rpm)
                    add(TargetFormat.Dmg)
//                if (getOs() == Os.Windows) {
//                    add(TargetFormat.AppImage) // portable distribution (installation-free)
//                }
                }.toTypedArray(),
            )
            packageName = "Ani"
            description = project.description
            vendor = "Him188"

            val projectVersion = project.version.toString() // 3.0.0-beta22
            macOS {
                dockName = "Animeko"
                pkgPackageVersion = projectVersion
                pkgPackageBuildVersion = projectVersion
                iconFile.set(file("icons/a_512x512.icns"))
//                iconFile.set(project(":app:shared").projectDir.resolve("androidRes/mipmap-xxxhdpi/a.png"))
                infoPlist {
                    extraKeysRawXml = macOSExtraPlistKeys
                }
            }
            windows {
                this.upgradeUuid = UUID.randomUUID().toString()
                iconFile.set(file("icons/a_1024x1024_rounded.ico"))
            }

            // adding copyright causes package to fail.
//            copyright = """
//                    Ani
//                    Copyright (C) 2022-2024 Him188
//
//                    This program is free software: you can redistribute it and/or modify
//                    it under the terms of the GNU General Public License as published by
//                    the Free Software Foundation, either version 3 of the License, or
//                    (at your option) any later version.
//
//                    This program is distributed in the hope that it will be useful,
//                    but WITHOUT ANY WARRANTY; without even the implied warranty of
//                    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//                    GNU General Public License for more details.
//
//                    You should have received a copy of the GNU General Public License
//                    along with this program.  If not, see <https://www.gnu.org/licenses/>.
//            """.trimIndent()
            licenseFile.set(rootProject.rootDir.resolve("LICENSE.txt"))
            packageVersion = properties["package.version"].toString()
        }

        buildTypes.release.proguard {
            isEnabled.set(false)
        }
        // TODO: Uncomment this to enable proguard for desktop, need some tweaks
//        buildTypes.release.proguard {
//            this.configurationFiles.from(project(":app:shared").file("proguard-rules.pro"))
//            this.configurationFiles.from(file("proguard-desktop.pro"))
//        }
    }
}

afterEvaluate {
    val os = getOs()
    when (os) {
        Os.Windows -> {
            tasks.named("createRuntimeImage", AbstractJLinkTask::class) {
                val dirsNames = listOf(
                    // From your (JBR's) Java Home to Packed Java Home 
                    "bin/jcef_helper.exe" to "bin/jcef_helper.exe",
                    "bin/icudtl.dat" to "bin/icudtl.dat",
                    "bin/v8_context_snapshot.bin" to "bin/v8_context_snapshot.bin",
                )

                dirsNames.forEach { (sourcePath, destPath) ->
                    val source = File(javaHome.get()).resolve(sourcePath)
                    inputs.file(source)
                    val dest = destinationDir.file(destPath)
                    outputs.file(dest)
                    doLast("copy $sourcePath") {
                        source.copyTo(dest.get().asFile)
                        logger.info("Copied $source to $dest")
                    }
                }
            }
        }
        Os.MacOS -> {
            val createRuntimeImage = tasks.named("createRuntimeImage", AbstractJLinkTask::class) {
                val dirsNames = listOf(
                    // From your (JBR's) Java Home to Packed Java Home 
                    "../Frameworks" to "lib/",
                )

                dirsNames.forEach { (sourcePath, destPath) ->
                    val source = File(javaHome.get()).resolve(sourcePath).normalize()
                    inputs.dir(source)
                    val dest = destinationDir.file(destPath)
                    outputs.dir(dest)
                    doLast("copy $sourcePath") {
                        ProcessBuilder().run {
                            command("cp", "-r", source.absolutePath, dest.get().asFile.normalize().absolutePath)
                            inheritIO()
                            start()
                        }.waitFor().let {
                            if (it != 0) {
                                throw GradleException("Failed to copy $sourcePath")
                            }
                        }
                        logger.info("Copied $source to $dest")
                    }
                }
            }
        }

        Os.Linux -> {}
        Os.Unknown -> {}
    }
}

val macOSExtraPlistKeys: String
    get() = """
        <key>CFBundleURLTypes</key>
        <array>
            <dict>
                <key>CFBundleURLName</key>
                <string>me.him188.ani</string>
                <key>CFBundleURLSchemes</key>
                <array>
                    <string>ani</string>
                </array>
            </dict>
        </array>
    """.trimIndent()

// workaround for CMP resources bug
tasks.withType(KotlinCompilationTask::class) {
    dependsOn("generateComposeResClass")
}

//kotlin.sourceSets.main.get().resources.srcDir(project(":common").projectDir.resolve("src/androidMain/res/raw"))

val anitorrentRootDir = rootProject.projectDir.resolve("torrent/anitorrent/anitorrent-native")
val anitorrentBuildDir = anitorrentRootDir.resolve("build-ci")

val copyAnitorrentDylibToResources = tasks.register("copyAnitorrentDylibToResources", Copy::class.java) {
    group = "anitorrent"
    dependsOn(":torrent:anitorrent:anitorrent-native:buildAnitorrent")

    val buildType = getPropertyOrNull("CMAKE_BUILD_TYPE") ?: "Debug"

    val libRelative = "anitorrent/lib"

    when (getOs()) {
        Os.Windows -> {
            from(anitorrentBuildDir.resolve("$buildType/anitorrent.dll"))
            from(anitorrentBuildDir.resolve("_deps/libtorrent-build/$buildType/torrent-rasterbar.dll"))
            into(projectDir.resolve("appResources/windows-x64").resolve(libRelative))
        }

        Os.MacOS -> {
            from(anitorrentBuildDir.resolve("libanitorrent.dylib"))
            from(anitorrentBuildDir.resolve("_deps/libtorrent-build/libtorrent-rasterbar.2.0.10.dylib"))
            val isArm = when (osArchitecture) {
                CpuArchitecture.X86 -> false
                CpuArchitecture.X86_64 -> false
                CpuArchitecture.ARM -> true
                CpuArchitecture.X86_ON_ARM -> false
                CpuArchitecture.UNKNOWN -> false
            }
            into(
                projectDir.resolve(
                    if (isArm) "appResources/macos-arm64"
                    else "appResources/macos-x64",
                ).resolve(libRelative),
            )
        }

        Os.Unknown, Os.Linux -> {
            from(anitorrentBuildDir.resolve("libanitorrent.so"))
            from(anitorrentBuildDir.resolve("_deps/libtorrent-build/libtorrent-rasterbar.2.0.10.so"))
            into(projectDir.resolve("appResources/linux-x64").resolve(libRelative))
        }
    }
}

// 复制 anitorrent cmake 构建出来的东西, 以及依赖的库到 appResources, 然后创建一个 anitorrent.deps.json 文件
val createDependencyManifest = tasks.register("createDependencyManifest") {
    dependsOn(":torrent:anitorrent:anitorrent-native:buildAnitorrent")
    val cmakeCache = anitorrentBuildDir.resolve("CMakeCache.txt")
    if (cmakeCache.exists()) {
        inputs.file(cmakeCache)
    }

    val projectDir = projectDir
    val depsFile = file("appResources/${getOsTriple()}/anitorrent/anitorrent.deps.json")
    outputs.file(depsFile)
    val targetDir = file("appResources/${getOsTriple()}/anitorrent/lib")
    outputs.dir(targetDir)

    val buildType = getPropertyOrNull("CMAKE_BUILD_TYPE") ?: "Debug"
    inputs.property("buildType", buildType)

    val anitorrentBuildDir = anitorrentBuildDir

    doLast {
        fun parseCMakeCache(cmakeCache: File): Map<String, String> {
            return cmakeCache.readText().lines().filterNot { it.startsWith("#") }.mapNotNull {
                val parts = it.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                parts[0].trim() to parts[1].trim()
            }.toMap()
        }

        val map = parseCMakeCache(cmakeCache)

        fun Map<String, String>.getOrFail(key: String): String {
            return this[key] ?: error("Key $key not found in CMakeCache")
        }

        val libraries = buildMap {
            map["OPENSSL_CRYPTO_LIBRARY:FILEPATH"]?.let {
                put("OPENSSL_CRYPTO_LIBRARY", File(it))
            }
            map["OPENSSL_SSL_LIBRARY:FILEPATH"]?.let {
                put("OPENSSL_SSL_LIBRARY", File(it))
            }

            if (isWindows) {
                // LIB_EAY_RELEASE:FILEPATH=C:/vcpkg/installed/x64-windows/lib/libcrypto.lib
                // SSL_EAY_RELEASE:FILEPATH=C:/vcpkg/installed/x64-windows/lib/libssl.lib
                fun findDll(libFile: File): List<File> {
                    val matched = libFile.parentFile.parentFile.resolve("bin")
                        .listFiles().orEmpty()
                        .filter { it.extension == "dll" && it.nameWithoutExtension.startsWith(libFile.nameWithoutExtension) }
                    return matched
                }

                fun findSystemDll(filename: String): File? {
                    val systemDir = File("C:/Windows/System32")
                    val systemDll = systemDir.resolve(filename)
                    if (systemDll.exists()) {
                        return systemDll
                    }
                    return null
                }
                findSystemDll("vcruntime140.dll")?.let {
                    put("vcruntime140", it)
                }
                findSystemDll("vcruntime140_1.dll")?.let {
                    put("vcruntime140_1", it)
                }
                findSystemDll("msvcp140.dll")?.let {
                    put("MSVCP140", it)
                }
                map["LIB_EAY_RELEASE:FILEPATH"]?.let {
                    findDll(File(it)).forEachIndexed { index, file ->
                        put("LIB_EAY_RELEASE_${index}", file)
                    }
                }
                map["SSL_EAY_RELEASE:FILEPATH"]?.let {
                    findDll(File(it)).forEachIndexed { index, file ->
                        put("SSL_EAY_RELEASE_${index}", file)
                    }
                }
            }
        }.toMutableMap()

        when (getOs()) {
            Os.Windows -> {
                if (anitorrentBuildDir.resolve("_deps/libtorrent-build/$buildType/torrent-rasterbar.dll").exists()) {
                    libraries["LIBTORRENT_RASTERBAR"] =
                        anitorrentBuildDir.resolve("_deps/libtorrent-build/$buildType/torrent-rasterbar.dll")
                    libraries["ANITORRENT"] = anitorrentBuildDir.resolve("$buildType/anitorrent.dll")
                } else {
                    libraries["LIBTORRENT_RASTERBAR"] =
                        anitorrentBuildDir.resolve("_deps/libtorrent-build/torrent-rasterbar.dll")
                    libraries["ANITORRENT"] = anitorrentBuildDir.resolve("anitorrent.dll")
                }
            }

            Os.MacOS -> {
                libraries["LIBTORRENT_RASTERBAR"] =
                    anitorrentBuildDir.resolve("_deps/libtorrent-build/libtorrent-rasterbar.2.0.10.dylib")
                libraries["ANITORRENT"] = anitorrentBuildDir.resolve("libanitorrent.dylib")
            }

            Os.Unknown, Os.Linux -> {
                libraries["LIBTORRENT_RASTERBAR"] =
                    anitorrentBuildDir.resolve("_deps/libtorrent-build/libtorrent-rasterbar.2.0.10.so")
                libraries["ANITORRENT"] = anitorrentBuildDir.resolve("libanitorrent.so")
            }
        }


        depsFile.writeText(Gson().toJson(libraries.mapValues { it.value.name }))

        for (library in libraries.values) {
            logger.info("Copying '$library' to '$targetDir'")
            library.toPath().toRealPath().toFile().copyTo(
                targetDir.resolve(projectDir.resolve(library).name),
                overwrite = true,
            )
        }
    }
}


if (enableAnitorrent) {
    tasks.named("processResources") {
        dependsOn(copyAnitorrentDylibToResources, createDependencyManifest)
    }

//  Reason: Task ':app:desktop:prepareAppResources' uses this output of task ':app:desktop:copyAnitorrentCppWrapperToResources' without declaring an explicit or implicit dependency. This can lead to incorrect results being produced, depending on what order the tasks are executed.
    afterEvaluate {
        tasks.named("prepareAppResources") {
            dependsOn(copyAnitorrentDylibToResources, createDependencyManifest)
        }
    }
} else {
    // file:///D:/Projects/animation-garden/app/desktop/build.gradle.kts:202:5:
    val readmeFile = anitorrentRootDir.resolve("README.md")
    // IDE 会识别这个格式并给出明显提示
    logger.warn("w:: Anitorrent 没有启用. PC 构建将不支持 BT 功能. Android 不受影响. 可阅读 $readmeFile 了解如何启用")
}


idea {
    module {
        excludeDirs.add(file("appResources/macos-x64/lib"))
        excludeDirs.add(file("appResources/macos-x64/plugins"))
        excludeDirs.add(file("appResources/macos-arm64/lib"))
        excludeDirs.add(file("appResources/macos-arm64/plugins"))
        excludeDirs.add(file("appResources/windows-x64/lib"))
        excludeDirs.add(file("test-sandbox"))
    }
}
