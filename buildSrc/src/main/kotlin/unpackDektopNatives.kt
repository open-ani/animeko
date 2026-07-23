/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
fun AbstractJPackageTask.unpackComposeDesktopNativeLibraries() {
    val triple = getOsTriple()
    val destinationDirFile = destinationDir.get().asFile

    // region: unpack native libraries of mediamp-mpv, mediamp-ffmpeg and anitorrent
    fun isRuntimePayloadJar(file: File): Boolean {
        if (!file.isFile || file.extension != "jar") {
            return false
        }
        val name = file.name
        return name.startsWith("mediamp-mpv-runtime-") ||
                name.startsWith("mediamp-ffmpeg-runtime-") ||
                (name.startsWith("anitorrent-native-desktop-") && name.contains("-$triple-"))
    }

    destinationDirFile
        .walk()
        .filter(::isRuntimePayloadJar)
        .toList()
        .forEach { jar ->
            unpackJar(jar, jar.parentFile) {
                !(it.name.contains("MANIFEST") || it.name.contains("META-INF"))
            }
            jar.delete()

            logger.lifecycle(
                "Extracted ${jar.name} into ${jar.parentFile} and deleted the jars",
            )
        }

    // endregion

    // region: unpack onnxruntime, re-pack the original onnx jar without runtime native libraries inside
    val onnxruntimeJar = destinationDirFile.walk()
        .find {
            it.isFile &&
                    it.extension == "jar" &&
                    it.name.startsWith("onnxruntime-")
        }
        ?: throw FileNotFoundException(
            "onnxruntime library jar doesn't exist at app runtime directory after compose jpackage task.",
        )
    val appRuntimeDir = onnxruntimeJar.parentFile.toPath()

    val (archPathInJar, nativeLibraryNames) = when (triple) {
        "windows-x64" -> "win-x64" to listOf(
            "onnxruntime.dll",
            "onnxruntime4j_jni.dll",
        )

        "macos-arm64" -> "osx-aarch64" to listOf(
            "libonnxruntime.dylib",
            "libonnxruntime4j_jni.dylib",
        )

        "macos-x64" -> "osx-x64" to listOf(
            "libonnxruntime.dylib",
            "libonnxruntime4j_jni.dylib",
        )

        "linux-x64" -> "linux-x64" to listOf(
            "libonnxruntime.so",
            "libonnxruntime4j_jni.so",
        )

        else -> {
            logger.lifecycle("$triple is not supported for Ani, ignoring unpack onnxruntime native library.")
            return
        }
    }

    val tempWorkDir = Files.createTempDirectory("ani-build-onnxruntime")
    val tempRepackedJar = tempWorkDir.resolve(onnxruntimeJar.name)
    try {
        extractNativesAndRepackOnnxRuntimeJar(
            onnxruntimeJar = onnxruntimeJar,
            repackedJar = tempRepackedJar,
            tempNativeDir = tempWorkDir,
            archPathInJar = archPathInJar,
            nativeLibraryNames = nativeLibraryNames,
        )

        nativeLibraryNames.forEach { libraryName ->
            Files.move(
                tempWorkDir.resolve(libraryName),
                appRuntimeDir.resolve(libraryName),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        Files.move(
            tempRepackedJar,
            onnxruntimeJar.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        logger.lifecycle(
            "Extracted ${nativeLibraryNames.joinToString()} for $archPathInJar " +
                    "and replaced onnxruntime jar with all native libraries stripped.",
        )
    } finally {
        tempWorkDir.deleteRecursively()
    }
    // endregion
}

fun AbstractJPackageTask.reconstructLinuxSolink() {
    destinationDir.get().asFile
        .walk()
        .filter { it.isDirectory && it.name == "app" && it.parentFile.name == "lib" }
        .flatMap { it.walk() }
        .filter { it.isFile && it.name.startsWith("lib") && it.extension == "so" }
        .forEach { library ->
            val process = ProcessBuilder("readelf", "-d", library.absolutePath)
                .redirectErrorStream(true)
                .start()
            val readElf = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() != 0) return@forEach
            val soname = Regex("Library soname: \\[(.+)]")
                .find(readElf)
                ?.groupValues
                ?.get(1)
                ?: return@forEach
            if (soname == library.name) return@forEach

            val alias = library.toPath().resolveSibling(soname)
            if (Files.notExists(alias)) {
                Files.createSymbolicLink(alias, library.toPath().fileName)
                logger.lifecycle("Created SONAME alias $alias -> ${library.name}")
            }
        }
}

private fun unpackJar(jar: File, dest: File, filter: (ZipEntry) -> Boolean = { true }) {
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

private const val ONNXRUNTIME_NATIVE_ROOT = "ai/onnxruntime/native/"

private fun extractNativesAndRepackOnnxRuntimeJar(
    onnxruntimeJar: File,
    repackedJar: Path,
    tempNativeDir: Path,
    archPathInJar: String,
    nativeLibraryNames: List<String>,
) {
    val requiredNativeEntries = nativeLibraryNames.associateBy { libraryName ->
        "$ONNXRUNTIME_NATIVE_ROOT$archPathInJar/$libraryName"
    }
    val missingNativeEntries = requiredNativeEntries.keys.toMutableSet()

    ZipOutputStream(
        BufferedOutputStream(
            Files.newOutputStream(
                repackedJar,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ),
        ),
    ).use { zipOutput ->
        ZipFile(onnxruntimeJar).use { sourceJar ->
            sourceJar.entries().asSequence().forEach { entry ->
                requiredNativeEntries[entry.name]?.let { libraryName ->
                    require(!entry.isDirectory) {
                        "Expected onnxruntime native library is a directory: ${entry.name}"
                    }
                    sourceJar.getInputStream(entry).use { input ->
                        Files.copy(
                            input,
                            tempNativeDir.resolve(libraryName),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    missingNativeEntries.remove(entry.name)
                }

                if (!entry.name.startsWith(ONNXRUNTIME_NATIVE_ROOT)) {
                    val repackedEntry = ZipEntry(entry.name).apply {
                        entry.comment?.let { comment = it }
                        entry.lastModifiedTime?.let { lastModifiedTime = it }
                    }
                    zipOutput.putNextEntry(repackedEntry)
                    if (!entry.isDirectory) {
                        sourceJar.getInputStream(entry).use { input ->
                            input.copyTo(zipOutput)
                        }
                    }
                    zipOutput.closeEntry()
                }
            }
        }
    }

    if (missingNativeEntries.isNotEmpty()) {
        throw FileNotFoundException(
            "onnxruntime native libraries don't exist in runtime jar: " +
                    missingNativeEntries.joinToString(),
        )
    }
}
