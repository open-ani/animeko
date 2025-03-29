/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

tasks.register("podInstall", Exec::class) {
    group = "build"
    description = "Builds the iOS framework"
    dependsOn(
        ":app:shared:application:podspec",
    )

    workingDir(projectDir)
    commandLine("pod", "install")
}

tasks.register("iosFramework") {
    group = "build"
    description = "Builds the iOS framework"
    dependsOn(
        ":app:shared:application:embedAndSignPodAppleFrameworkForXcode",
    )
}

fun ipaArguments(
    destination: String = "generic/platform=iOS",
    sdk: String = "iphoneos",
): Array<String> {
    return arrayOf(
        "xcodebuild",
        "-workspace", "Animeko.xcworkspace",
        "-scheme", "Animeko",
        "-destination", destination,
        "-sdk", sdk,
        "CODE_SIGNING_ALLOWED=NO",
        "CODE_SIGNING_REQUIRED=NO",
    )
}


val buildDebugArchive = tasks.register("buildDebugArchive", Exec::class) {
    group = "build"
    description = "Builds the iOS framework for Debug"
    workingDir(projectDir)

    val output = layout.buildDirectory.file("archives/debug/Animeko.xcarchive")
    outputs.file(output)
    commandLine(
        *ipaArguments(),
        "archive",
        "-configuration", "Debug",
        "-archivePath", output.get().asFile.absolutePath,
    )
}

val buildReleaseArchive = tasks.register("buildReleaseArchive", Exec::class) {
    group = "build"
    description = "Builds the iOS framework for Debug"
    workingDir(projectDir)

    val output = layout.buildDirectory.file("archives/release/Animeko.xcarchive")
    outputs.file(output)
    commandLine(
        *ipaArguments(),
        "archive",
        "-configuration", "Release",
        "-archivePath", output.get().asFile.absolutePath,
    )
}


/**
 * A Gradle task that packages an unsigned .ipa manually from an .xcarchive.
 */
@CacheableTask
abstract class BuildIpaTask(
//    project: Project,
) : DefaultTask() {

    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    @get:InputDirectory
    abstract val archiveDir: DirectoryProperty

    @get:OutputFile
    abstract val outputIpa: RegularFileProperty

    @TaskAction
    fun buildIpa() {
        // 1. Locate the .app in the .xcarchive
        val appDir = archiveDir.get().asFile.resolve("Products/Applications/Animeko.app")
        if (!appDir.exists()) {
            throw GradleException("Could not find Animeko.app in archive at: ${appDir.absolutePath}")
        }

        // 2. Create a temporary Payload folder
        val payloadDir = File(temporaryDir, "Payload").apply { mkdirs() }
        val destApp = File(payloadDir, "Animeko.app")

        // 3. Copy the .app into Payload/
        appDir.copyRecursively(destApp, overwrite = true)

        // 4. Zip the Payload folder
        val zipFile = File(temporaryDir, "Animeko.zip")
        zipDirectory(payloadDir, zipFile)

        // 5. Rename .zip to .ipa
        val ipaFile = outputIpa.get().asFile
        ipaFile.parentFile.mkdirs()
        if (ipaFile.exists()) ipaFile.delete()
        zipFile.renameTo(ipaFile)

        logger.lifecycle("Created unsigned IPA at: ${ipaFile.absolutePath}")
    }

    /**
     * Zips the given [sourceDir] (including all subdirectories) into [outputFile].
     */
    private fun zipDirectory(sourceDir: File, outputFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zipOut ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(sourceDir.parentFile).path
                    val zipEntry = ZipEntry(relativePath)
                    zipOut.putNextEntry(zipEntry)
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }
}


tasks.register("buildDebugIpa", BuildIpaTask::class) {
    description = "Manually packages the .app from the .xcarchive into an unsigned .ipa"
    group = "build"

    // Adjust these paths as needed
    archiveDir = layout.projectDirectory.dir("build/archives/debug/Animeko.xcarchive")
    outputIpa = layout.projectDirectory.file("build/archives/debug/Animeko.ipa")
    dependsOn(buildDebugArchive)
}

tasks.register("buildReleaseIpa", BuildIpaTask::class) {
    description = "Manually packages the .app from the .xcarchive into an unsigned .ipa"
    group = "build"

    // Adjust these paths as needed
    archiveDir = layout.projectDirectory.dir("build/archives/release/Animeko.xcarchive")
    outputIpa = layout.projectDirectory.file("build/archives/release/Animeko.ipa")
    dependsOn(buildReleaseArchive)
}

//tasks.register("buildDebugIpa", Exec::class) {
//    val archiveFile = buildDebugArchive.get().outputs.files.singleFile
//    val output = projectDir.resolve("build/archives/Animeko.ipa")
//    outputs.file(output)
//    commandLine(
//        "xcodebuild",
//        "-exportArchive",
//        "-archivePath", archiveFile.absolutePath,
//        "-exportPath", output.absolutePath,
//        "-exportOptionsPlist", projectDir.resolve("exportOptions.plist").absolutePath,
//        "CODE_SIGNING_ALLOWED=NO",
//        "CODE_SIGNING_REQUIRED=NO",
//    )
//}

tasks.register("buildReleaseArchive", Exec::class) {
    group = "build"
    description = "Builds the iOS framework for Release"
    workingDir(projectDir)

    val output = layout.buildDirectory.file("archives/Animeko.xcarchive")
    outputs.file(output)
    commandLine(
        *ipaArguments(),
        "archive",
        "-configuration", "Release",
        "-archivePath", output.get().asFile.absolutePath,
    )
}

/// FOR DEBUG

fun simulatorAppPath(): Provider<RegularFile> =
    // Adjust this path if your build output is located differently.
    layout.buildDirectory.file("Debug-iphonesimulator/Animeko.app")

tasks.register("buildDebugForSimulator", Exec::class) {
    group = "build"
    description = "Builds a debug version of Animeko for iOS Simulator"
    val destination = project.getLocalProperty("ani.ios.simulator.destination") ?: "generic/platform=iOS Simulator"
    inputs.property("destination", destination)
    workingDir(projectDir)
    commandLine(
        *ipaArguments(
            destination = destination,
            sdk = "iphonesimulator",
        ),
        "-configuration", "Debug",
        "build",
    )
}

tasks.register("launchSimulator", Exec::class) {
    group = "run"
    description = "Launches the iOS simulator"

    // This opens the default iOS Simulator app on macOS. 
    // You can also explicitly boot a specific device via `xcrun simctl boot "iPhone 14"` 
    // if you want more control.
    commandLine("open", "-a", "Simulator")
}

tasks.register("installDebugOnSimulator", Exec::class) {
    group = "run"
    description = "Installs the debug build on the Simulator"
    dependsOn("buildDebugForSimulator", "launchSimulator")

    val appPath = simulatorAppPath()
    // Typically, you want to ensure the simulator is booted before installing.
    commandLine("xcrun", "simctl", "install", "booted", appPath.get().asFile.absolutePath)
}

tasks.register("launchAppOnSimulator", Exec::class) {
    group = "run"
    description = "Launches the Animeko app on the simulator"
    dependsOn("installDebugOnSimulator")

    commandLine("xcrun", "simctl", "launch", "booted", "org.openani.Animeko")
}
