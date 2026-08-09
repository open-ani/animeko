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

/**
 * Directory inside `lib/app` that holds the bundled libraries which are not JNI entry points.
 */
private const val ISOLATED_LIBRARY_DIR = "native"

/**
 * Libraries that stay in `lib/app` even though they export no JNI symbol.
 *
 * `ai.onnxruntime.OnnxRuntime.load` calls `System.loadLibrary("onnxruntime")` before loading the JNI
 * library, so this one has to remain resolvable through `java.library.path`. Isolating it makes that
 * call fall through to the system onnxruntime, which is then already loaded under the same SONAME by
 * the time the JNI library is linked — the `DT_RPATH` below cannot override an image that is already
 * in the process.
 */
private val KEPT_NON_JNI_LIBRARIES = setOf("libonnxruntime.so")

/**
 * The only libraries allowed to stay in `lib/app`.
 *
 * Deciding this against the build machine's `ldconfig` would only ever catch the names that machine
 * happens to package: Ubuntu, where the release is built, ships no onnxruntime, while Arch does — so
 * the very library that had to stay is the one such a check would wave through. The rule is therefore
 * inverted. A library stays because it is listed here, not because some distribution does not ship a
 * library of the same name.
 */
private val ALLOWED_ON_LIBRARY_PATH = setOf(
    "libskiko-linux-x64.so",
    "libmediampv.so",
    "libanitorrent.so",
    "libffmpegkitjni.so",
    "libonnxruntime4j_jni.so",
    // See KEPT_NON_JNI_LIBRARIES for why these two cannot be isolated.
    "libonnxruntime.so",
    "libonnxruntime.so.1",
)

private val SHARED_LIBRARY_NAME = Regex("""^lib.*\.so(\.\d+)*$""")

/**
 * Moves every bundled library that is not a JNI entry point out of `lib/app` into `lib/app/native`,
 * and gives each of them a `DT_RPATH` of `$ORIGIN` so they only ever resolve against each other.
 *
 * jpackage makes `lib/app` the `java.library.path`, and [unpackComposeDesktopNativeLibraries] flattens
 * the mediamp runtime jars into that same directory. That puts ~45 generically named system libraries
 * (`libglib-2.0.so.0`, `libssl.so.3`, `libz.so.1`, `libX*`, `libav*`, ...) on a search path that is
 * visible to everything else in the process. Whoever asks for one of those SONAMEs first wins, so the
 * bundled copy can shadow the system one for an unrelated consumer: #3259 is CEF loading the system
 * `libgobject-2.0.so.0` against the bundled `libglib-2.0.so.0`, which then cannot resolve `g_string_copy`.
 *
 * `AppRun` cannot fix this. CEF builds the environment for `jcef_helper` itself and appends the
 * `java.library.path` entries to the child's `LD_LIBRARY_PATH`, so the directory reaches the helper
 * process no matter what the launcher script exports.
 *
 * `--force-rpath` is deliberate. `DT_RUNPATH` — which is what the upstream libraries ship — loses to
 * `LD_LIBRARY_PATH`, so a user with their own `LD_LIBRARY_PATH` can still feed mpv a mismatched FFmpeg.
 * `DT_RPATH` wins over it and is inherited along the dependency chain.
 */
fun AbstractJPackageTask.isolateLinuxBundledLibraries() {
    if (getOsTriple() != "linux-x64") {
        return
    }
    val appDir = destinationDir.get().asFile
        .walk()
        .firstOrNull { it.isDirectory && it.name == "app" && it.parentFile.name == "lib" }
        ?: throw FileNotFoundException(
            "app runtime directory doesn't exist after compose jpackage task.",
        )

    requirePatchelf()

    // A library and the SONAME symlinks reconstructLinuxSolink created for it move together.
    val groups = appDir.listFiles()
        ?.toList()
        .orEmpty()
        .filter { SHARED_LIBRARY_NAME.matches(it.name) }
        .groupBy { it.toPath().toRealPath().toFile() }

    val (kept, isolated) = groups.entries.partition { (realFile, names) ->
        names.any { it.name in KEPT_NON_JNI_LIBRARIES } || exportsJniSymbols(realFile)
    }

    val isolatedDir = appDir.resolve(ISOLATED_LIBRARY_DIR)
    isolatedDir.mkdirs()
    val isolatedNames = mutableSetOf<String>()

    isolated.forEach { (realFile, names) ->
        check(realFile.parentFile == appDir) {
            "$realFile is linked from ${appDir.name} but does not live there; refusing to move it."
        }
        // Only the aliases are removed here — realFile is one of `names` and has yet to be moved.
        val links = names.filter { Files.isSymbolicLink(it.toPath()) }
            .map { it.name to Files.readSymbolicLink(it.toPath()) }
        links.forEach { (name, _) -> appDir.resolve(name).delete() }

        Files.move(realFile.toPath(), isolatedDir.resolve(realFile.name).toPath())
        links.forEach { (name, target) ->
            Files.createSymbolicLink(isolatedDir.resolve(name).toPath(), target)
        }

        forceRpath(isolatedDir.resolve(realFile.name), "\$ORIGIN")
        isolatedNames += names.map { it.name }
    }

    // Only the entry points that actually depend on a bundled library are rewritten. libskiko ships a
    // .sha256 sidecar that it verifies on load, so patching it unconditionally would break startup.
    val bundledNames = isolatedNames + kept.flatMap { (_, names) -> names.map { it.name } }
    kept.forEach { (realFile, _) ->
        if (readNeededLibraries(realFile).none { it in bundledNames }) {
            return@forEach
        }
        forceRpath(realFile, "\$ORIGIN/$ISOLATED_LIBRARY_DIR:\$ORIGIN")
    }

    checkOnlyAllowedLibrariesRemain(appDir)

    logger.lifecycle(
        "Isolated ${isolated.size} bundled libraries into $isolatedDir, " +
                "kept ${kept.joinToString { it.key.name }} on java.library.path.",
    )
}

/**
 * Fails the build when anything outside [ALLOWED_ON_LIBRARY_PATH] is left in `lib/app`.
 *
 * jpackage exposes that directory as `java.library.path` and `AppRun` puts it on `LD_LIBRARY_PATH`,
 * so whatever sits there is reachable by SONAME from every other consumer in the process — including
 * the system libraries CEF loads. That is the shape of #3259.
 */
private fun checkOnlyAllowedLibrariesRemain(appDir: File) {
    val unexpected = appDir.listFiles()
        ?.toList()
        .orEmpty()
        .map { it.name }
        .filter { SHARED_LIBRARY_NAME.matches(it) }
        .filter { it !in ALLOWED_ON_LIBRARY_PATH }

    check(unexpected.isEmpty()) {
        "Bundled libraries left on java.library.path are not allow-listed: " +
                "${unexpected.joinToString()}. They can shadow the system library of the same SONAME " +
                "for an unrelated consumer such as CEF (#3259). Isolate them, or add them to " +
                "ALLOWED_ON_LIBRARY_PATH once the collision is understood and harmless."
    }
}

/**
 * Runtime programs that are meant to stay non-executable.
 *
 * JBR ships `chrome-sandbox` as `0644` because the setuid sandbox has to be installed as root by
 * whoever distributes the app. CEF falls back to the user namespace sandbox when the helper is not
 * usable, so marking it executable without the setuid bit only moves the failure.
 */
private val NON_EXECUTABLE_RUNTIME_PROGRAMS = setOf("chrome-sandbox")

/**
 * Restores the executable bit on the runtime's helper programs.
 *
 * jpackage copies the JBR into `lib/runtime` without preserving permissions, so `jcef_helper` and
 * `cef_server` arrive as `0644` even though JBR ships them `0755`. CEF then cannot spawn its child
 * processes: `failed to execvp`, every GPU and network service launch fails, and the browser process
 * aborts with `GPU process isn't usable`.
 *
 * The AppImage job worked around this with a `chmod a+x` of its own, which leaves every other
 * consumer of `createReleaseDistributable` — the deb and rpm packages among them — broken. Doing it
 * here covers all of them.
 *
 * Programs are told apart from shared libraries by their `PT_INTERP` segment, so a helper added by a
 * future JBR is picked up without editing a list here.
 */
fun AbstractJPackageTask.restoreLinuxRuntimeExecutables() {
    if (getOsTriple() != "linux-x64") {
        return
    }
    val runtimeLibDir = destinationDir.get().asFile
        .walk()
        .firstOrNull { it.isDirectory && it.name == "lib" && it.parentFile.name == "runtime" }
        ?: throw FileNotFoundException(
            "runtime library directory doesn't exist after compose jpackage task.",
        )

    val restored = runtimeLibDir.listFiles()
        ?.toList()
        .orEmpty()
        .filter { it.isFile && !it.canExecute() && it.name !in NON_EXECUTABLE_RUNTIME_PROGRAMS }
        .filter { isElfProgram(it) }
        .onEach {
            check(it.setExecutable(true, false)) { "Failed to mark $it executable." }
        }

    if (restored.isEmpty()) {
        logger.info("No runtime helper program needed its executable bit restored.")
    } else {
        logger.lifecycle("Restored the executable bit on ${restored.joinToString { it.name }}.")
    }
}

/**
 * Whether [file] is an ELF program rather than a shared library. Only programs carry `PT_INTERP`.
 */
private fun isElfProgram(file: File): Boolean {
    val programHeaders = runCommandOrNull("readelf", "-lW", file.absolutePath) ?: return false
    return programHeaders.lineSequence().any { it.trimStart().startsWith("INTERP") }
}

private fun exportsJniSymbols(library: File): Boolean {
    val symbols = runCommandOrNull("readelf", "-W", "--dyn-syms", library.absolutePath) ?: return false
    return symbols.lineSequence().any { it.contains(" Java_") || it.contains(" JNI_OnLoad") }
}

private fun readNeededLibraries(library: File): List<String> {
    val dynamic = runCommandOrNull("readelf", "-d", library.absolutePath) ?: return emptyList()
    return Regex("""Shared library: \[(.+?)]""")
        .findAll(dynamic)
        .map { it.groupValues[1] }
        .toList()
}

private fun forceRpath(library: File, rpath: String) {
    checkNotNull(runCommandOrNull("patchelf", "--force-rpath", "--set-rpath", rpath, library.absolutePath)) {
        "patchelf failed to set RPATH '$rpath' on $library."
    }
    // patchelf exits 0 whether or not `--force-rpath` took effect, and a leftover DT_RUNPATH loses to
    // LD_LIBRARY_PATH — which is the override this whole function exists to prevent. Read it back.
    val dynamic = checkNotNull(runCommandOrNull("readelf", "-dW", library.absolutePath)) {
        "readelf failed to read the dynamic section of $library."
    }
    check(!dynamic.contains("(RUNPATH)")) {
        "$library still carries a DT_RUNPATH after --force-rpath; it would lose to LD_LIBRARY_PATH."
    }
    val actual = Regex("""\(RPATH\)\s+Library rpath: \[(.*?)]""").find(dynamic)?.groupValues?.get(1)
    check(actual == rpath) {
        "$library has DT_RPATH '$actual' but '$rpath' was requested."
    }
}

private fun requirePatchelf() {
    checkNotNull(runCommandOrNull("patchelf", "--version")) {
        "patchelf is required to package the Linux distribution but is not available on PATH."
    }
}

/**
 * Returns the combined output of [command], or `null` if it could not be started or exited non-zero.
 */
private fun runCommandOrNull(vararg command: String): String? {
    val process = try {
        ProcessBuilder(*command).redirectErrorStream(true).start()
    } catch (e: java.io.IOException) {
        return null
    }
    val output = process.inputStream.bufferedReader().use { it.readText() }
    return output.takeIf { process.waitFor() == 0 }
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
