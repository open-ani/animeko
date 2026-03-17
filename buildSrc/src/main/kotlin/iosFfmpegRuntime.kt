/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Copies FFmpeg runtime files into an iOS app bundle and may re-sign the bundle")
abstract class EmbedIosFfmpegRuntimeTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val runtimeDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val appBundleDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val signingIdentity: Property<String>

    @get:InputFile
    @get:Optional
    abstract val signingKeychain: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun embed() {
        val runtimeDir = runtimeDirectory.get().asFile
        require(runtimeDir.isDirectory) {
            "FFmpeg runtime directory does not exist: ${runtimeDir.absolutePath}"
        }

        val appBundleDir = appBundleDirectory.get().asFile
        if (!appBundleDir.isDirectory) {
            logger.lifecycle("Skipping FFmpeg runtime embedding because the app bundle does not exist yet: ${appBundleDir.absolutePath}")
            return
        }

        val runtimeFiles = runtimeDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            .orEmpty()
        require(runtimeFiles.isNotEmpty()) {
            "No FFmpeg runtime files found in ${runtimeDir.absolutePath}"
        }

        val runtimeBundleDir = appBundleDir.resolve("FFmpegRuntime").apply {
            deleteRecursively()
            mkdirs()
        }
        val copiedCodeFiles = mutableListOf<File>()
        runtimeFiles.forEach { src ->
            val dest = runtimeBundleDir.resolve(src.name)
            src.copyTo(dest, overwrite = true)
            if (src.name == "ffmpeg" || src.name.endsWith(".dylib")) {
                dest.setExecutable(true, false)
                copiedCodeFiles += dest
            }
        }

        val identity = signingIdentity.orNull?.trim()?.takeIf { it.isNotEmpty() } ?: return
        copiedCodeFiles.forEach { signCodeFile(it, identity) }
        signAppBundle(appBundleDir, identity)
    }

    private fun signCodeFile(file: File, identity: String) {
        execOperations.exec {
            commandLine(
                buildList {
                    add("codesign")
                    add("--force")
                    add("--sign")
                    add(identity)
                    add("--timestamp=none")
                    signingKeychain.orNull?.let {
                        add("--keychain")
                        add(it.asFile.absolutePath)
                    }
                    add(file.absolutePath)
                },
            )
        }
    }

    private fun signAppBundle(appBundleDir: File, identity: String) {
        execOperations.exec {
            commandLine(
                buildList {
                    add("codesign")
                    add("--force")
                    add("--sign")
                    add(identity)
                    add("--timestamp=none")
                    add("--preserve-metadata=identifier,entitlements,flags")
                    signingKeychain.orNull?.let {
                        add("--keychain")
                        add(it.asFile.absolutePath)
                    }
                    add(appBundleDir.absolutePath)
                },
            )
        }
    }
}
