/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

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
            "-XX:+UseZGC",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=TRACE",
            "-Dsun.java2d.metal=true",
            "-Djogamp.debug.JNILibLoader=true", // JCEF 加载 native 库的日志, 方便 debug
            // JCEF
            "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
            "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
            "-Xmx256m",
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

        if (getLocalProperty("ani.desktop.proguard")?.toBooleanStrict() != false) {
            buildTypes.release.proguard {
                isEnabled.set(true)
                version = "7.6.1"
                optimize.set(true)
                obfuscate.set(false)
                this.configurationFiles.from(project(":app:shared").sharedAndroidProguardRules())
                this.configurationFiles.from(file("proguard-desktop.pro"))
            }
        }

//        tasks.withType<AbstractProguardTask> {
        // inputFiles 实际上会添加到 runtime 打包, 但应该会被 optimize 掉
        // CMP 不提供以 -libraryjar 方式添加的方法, 所以只能这样了
//            inputFiles.from(
//                // 补上缺少的 compileOnly 依赖
//                project.configurations.detachedConfiguration(
//                    project.dependencies.create("com.google.code.findbugs:annotations:3.0.1"),
//                    project.dependencies.create("com.google.code.findbugs:jsr305:3.0.1"),
//                    project.dependencies.create("org.jspecify:jspecify:1.0.0"),
//                    project.dependencies.create("org.osgi:org.osgi.core:6.0.0"),
//                    project.dependencies.create("org.osgi:org.osgi.annotation.versioning:1.1.2"),
//                    project.dependencies.create("com.lmax:disruptor:3.4.2"),
//                    project.dependencies.create("com.fasterxml.jackson.core:jackson-annotations:2.12.0"),
////                    project.dependencies.create("jakarta.jms:jakarta.jms-api:3.1.0"),
//                    project.dependencies.create("org.junit.jupiter:junit-jupiter-api:5.11.4"),
//                    project.dependencies.create("org.apache.commons:commons-csv:1.12.0"),
//                    project.dependencies.create("com.fasterxml.jackson.core:jackson-databind:2.13.1"),
//                    project.dependencies.create("org.apiguardian:apiguardian-api:1.1.2"), // log4j
//                    project.dependencies.create("org.zeromq:jeromq:0.5.3"),
//                    project.dependencies.create("org.ow2.asm:asm:9.7"),// com.google.common.truth.ActualValueInference$InferenceClassVisitor
//                    project.dependencies.create("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.1"), // log4j
//                    project.dependencies.create("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.2"), // log4j
//                    project.dependencies.create("org.fusesource.jansi:jansi:2.4.0"), // log4j
//                    project.dependencies.create("org.jctools:jctools-core:4.0.1"), // log4j
//                    project.dependencies.create("biz.aQute.bnd:biz.aQute.bnd.annotation:6.3.1"), // com.ctc.wstx.msv.W3CSchemaFactory:
//                    project.dependencies.create("io.projectreactor.tools:blockhound:1.0.10.RELEASE"), // coroutines
//                    project.dependencies.create("org.conscrypt:conscrypt-openjdk:2.5.2") // okhttp
//                ),
//            )
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
            tasks.named("createRuntimeImage", AbstractJLinkTask::class) {
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

tasks.withType(AbstractJPackageTask::class) {
    doLast {
        fun unpackJar(jar: File, dest: File, filter: (ZipEntry) -> Boolean = { true }) {
            val zip = ZipFile(jar)
            zip.use {
                zip.entries().asSequence().filter(filter).forEach { entry ->
                    val file = dest.resolve(entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }

        val jarsToUnpack = listOf("anitorrent-native")

        destinationDir.get().asFile.walk().firstOrNull { file ->
            jarsToUnpack.any { file.name.startsWith(it) && file.extension == "jar" }
        }?.let { file ->
            unpackJar(file, file.parentFile) {
                it.name.endsWith("dylib") || it.name.endsWith("so") || it.name.endsWith("dll")
            }
        }
    }
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
