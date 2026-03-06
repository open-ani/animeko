/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

tasks.register("podInstall", Exec::class) {
    group = "build"
    description = "Builds the iOS framework"
    dependsOn(
        ":app:shared:application:podspec",
        patchInfoPlist,
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

object IpaArguments {
    fun create(
        workspace: String = "Animeko.xcworkspace",
        scheme: String = "Animeko",
        destination: String = "generic/platform=iOS",
        sdk: String = "iphoneos",
        codeSigningAllowed: Boolean? = null,
        codeSigningRequired: Boolean? = null,
        additionalBuildSettings: Map<String, String> = emptyMap(),
    ): List<String> {
        return buildList {
            addAll(
                listOf(
                    "xcodebuild",
                    "-workspace", workspace,
                    "-scheme", scheme,
                    "-destination", destination,
                    "-sdk", sdk,
                ),
            )
            codeSigningAllowed?.let {
                val codeSignAllowedValue = if (it) "YES" else "NO"
                add("CODE_SIGNING_ALLOWED=$codeSignAllowedValue")
            }
            codeSigningRequired?.let {
                val codeSignRequiredValue = if (it) "YES" else "NO"
                add("CODE_SIGNING_REQUIRED=$codeSignRequiredValue")
            }
            additionalBuildSettings.forEach { (key, value) ->
                add("$key=$value")
            }
        }
    }
}

fun ipaArguments(
    workspace: String = "Animeko.xcworkspace",
    scheme: String = "Animeko",
    destination: String = "generic/platform=iOS",
    sdk: String = "iphoneos",
    codeSigningAllowed: Boolean? = null,
    codeSigningRequired: Boolean? = null,
    additionalBuildSettings: Map<String, String> = emptyMap(),
): List<String> {
    return IpaArguments.create(
        workspace = workspace,
        scheme = scheme,
        destination = destination,
        sdk = sdk,
        codeSigningAllowed = codeSigningAllowed,
        codeSigningRequired = codeSigningRequired,
        additionalBuildSettings = additionalBuildSettings,
    )
}

object ProvisioningProfileUtils {
    data class Parsed(
        val decoded: ByteArray,
        val uuid: String,
        val name: String,
    )

    fun decode(profileBase64Raw: String): Parsed {
        val profileBase64 = profileBase64Raw.trim().removeSurrounding("\"")
        if (profileBase64.isBlank()) {
            throw GradleException("APPSTORE_PROVISIONING_PROFILE is not set.")
        }

        val sanitizedBase64 = profileBase64.replace(Regex("\\s+"), "")
        val decoded = try {
            Base64.getMimeDecoder().decode(sanitizedBase64)
        } catch (e: IllegalArgumentException) {
            throw GradleException(
                "APPSTORE_PROVISIONING_PROFILE is not valid base64. Remove wrapping quotes and preserve raw base64 content.",
                e,
            )
        }
        val content = String(decoded, Charsets.ISO_8859_1)
        val uuidRegex = Regex("""<key>UUID</key>\s*<string>([^<]+)</string>""")
        val uuid = uuidRegex.find(content)?.groupValues?.get(1)
            ?: throw GradleException("Could not extract UUID from provisioning profile")
        val nameRegex = Regex("""<key>Name</key>\s*<string>([^<]+)</string>""")
        val profileName = nameRegex.find(content)?.groupValues?.get(1)
            ?: throw GradleException("Could not extract Name from provisioning profile")

        return Parsed(
            decoded = decoded,
            uuid = uuid,
            name = profileName,
        )
    }
}

@DisableCachingByDefault(because = "Runs xcodebuild, which depends on local Xcode and signing state")
abstract class XcodeArchiveTask : DefaultTask() {
    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workspaceDirectory: DirectoryProperty

    @get:Input
    abstract val scheme: Property<String>

    @get:Input
    abstract val destination: Property<String>

    @get:Input
    abstract val sdk: Property<String>

    @get:Input
    abstract val archiveConfiguration: Property<String>

    @get:Input
    abstract val codeSigningAllowed: Property<Boolean>

    @get:Input
    abstract val codeSigningRequired: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val codeSignStyle: Property<String>

    @get:Input
    @get:Optional
    abstract val developmentTeamId: Property<String>

    @get:Input
    @get:Optional
    abstract val profileName: Property<String>

    @get:Input
    @get:Optional
    abstract val signingKeychain: Property<String>

    @get:OutputDirectory
    abstract val archivePath: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun buildArchive() {
        val additionalBuildSettings = mutableMapOf<String, String>()
        codeSignStyle.orNull?.takeIf { it.isNotBlank() }?.let {
            additionalBuildSettings["CODE_SIGN_STYLE"] = it
        }
        developmentTeamId.orNull?.takeIf { it.isNotBlank() }?.let {
            additionalBuildSettings["DEVELOPMENT_TEAM"] = it
        }
        if (codeSigningAllowed.get()
            && (codeSignStyle.orNull == "Automatic" || codeSignStyle.orNull == "Manual")
            && developmentTeamId.orNull.isNullOrBlank()
        ) {
            throw GradleException("APPLE_DEVELOPER_TEAM_ID is required for signed archive builds.")
        }
        if (codeSigningAllowed.get() && codeSignStyle.orNull == "Manual") {
            val resolvedProfileName = profileName.orNull?.takeIf { it.isNotBlank() }
                ?: throw GradleException("Provisioning profile Name is required for Manual signed archive builds.")
            additionalBuildSettings["PROVISIONING_PROFILE_SPECIFIER"] = resolvedProfileName
            additionalBuildSettings["CODE_SIGN_IDENTITY"] = "Apple Distribution"
            signingKeychain.orNull?.let { keychain ->
                additionalBuildSettings["OTHER_CODE_SIGN_FLAGS"] = "--keychain $keychain"
            }
        }

        val args = buildList {
            val forceGlobalCodeSigningFlags = !(codeSigningAllowed.get() && codeSigningRequired.get())
            addAll(
                IpaArguments.create(
                    workspace = workspaceDirectory.get().asFile.absolutePath,
                    scheme = scheme.get(),
                    destination = destination.get(),
                    sdk = sdk.get(),
                    codeSigningAllowed = if (forceGlobalCodeSigningFlags) codeSigningAllowed.get() else null,
                    codeSigningRequired = if (forceGlobalCodeSigningFlags) codeSigningRequired.get() else null,
                    additionalBuildSettings = additionalBuildSettings,
                ),
            )
            add("archive")
            add("-configuration")
            add(archiveConfiguration.get())
            add("-archivePath")
            add(archivePath.get().asFile.absolutePath)
        }

        execOperations.exec {
            workingDir(workingDirectory.get().asFile)
            commandLine(args)
        }

        signingKeychain.orNull?.let { keychain ->
            // 尝试删除已有的 keychains, 在 CI 上肯定没有, 本地测试的时候可能会有
            execOperations.exec {
                commandLine("sh", "-c", "security delete-keychain \"$keychain\" 2>/dev/null || true")
            }
        }
    }
}

@DisableCachingByDefault(because = "Creates and unlocks an ephemeral macOS keychain for codesigning")
abstract class PrepareBuildKeychainTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val privateKeyPfxBase64: Property<String>

    @get:Input
    @get:Optional
    abstract val privateKeyPfxPassword: Property<String>

    @get:OutputFile
    abstract val outKeychain: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun prepare() {
        val encodedPfx = privateKeyPfxBase64.orNull?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX is not set.")
        val importPassword = privateKeyPfxPassword.orNull?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX_IMPORT_PWD is not set.")
        val keychainPassword = UUID.randomUUID().toString()

        val outKeychainFilePath = outKeychain.asFile.get().absolutePath
        val tempPfxKeyFile = temporaryDir.resolve("apple_distribution_key.p12").apply {
            val decoded = try {
                Base64.getMimeDecoder().decode(encodedPfx.replace(Regex("\\s+"), ""))
            } catch (e: IllegalArgumentException) {
                throw GradleException("APPLE_DISTRIBUTION_PRIVATE_KEY is not valid base64 content.", e)
            }
            writeBytes(decoded)
        }

        // 尝试删除已有的 keychains, 在 CI 上肯定没有, 本地测试的时候可能会有
        execOperations.exec {
            commandLine("sh", "-c", "security delete-keychain \"$outKeychainFilePath\" 2>/dev/null || true")
        }
        // 用随机密码创建 keychain
        execOperations.exec { commandLine("security", "create-keychain", "-p", keychainPassword, outKeychainFilePath) }
        // 设置解锁后重新上锁的时间
        execOperations.exec {
            commandLine("security", "set-keychain-settings", "-lut", "21600", "-u", outKeychainFilePath)
        }
        // 导入 apple distribution certificates
        execOperations.exec {
            commandLine(
                "security", "import", tempPfxKeyFile.absolutePath,
                "-k", outKeychainFilePath,
                "-P", importPassword,
                "-T", "/usr/bin/codesign",
                "-T", "/usr/bin/security",
                "-T", "/usr/bin/xcodebuild",
                "-T", "/usr/bin/productbuild",
            )
        }
        // 
        val partitionOutput = ByteArrayOutputStream()
        execOperations.exec {

            commandLine(
                "security", "set-key-partition-list",
                "-S", "apple-tool:,apple:,codesign:",
                "-s",
                "-k", keychainPassword,
                outKeychainFilePath,
            )
            standardOutput = partitionOutput
        }
        partitionOutput.close()

        // 导入 Intermediate Certificates
        val curlOutput = ByteArrayOutputStream()
        kotlin.run {

            val g3 = temporaryDir.resolve("AppleWWDRCAG3.cer").absolutePath
            val g4 = temporaryDir.resolve("AppleWWDRCAG4.cer").absolutePath
            val g2 = temporaryDir.resolve("DeveloperIDG2CA.cer").absolutePath

            execOperations.exec {
                commandLine("curl", "-o", g3, "https://www.apple.com/certificateauthority/AppleWWDRCAG3.cer")
                standardOutput = curlOutput
            }
            execOperations.exec { commandLine("security", "import", g3, "-k", outKeychainFilePath) }
            execOperations.exec {
                commandLine("curl", "-o", g4, "https://www.apple.com/certificateauthority/AppleWWDRCAG4.cer")
                standardOutput = curlOutput
            }
            execOperations.exec { commandLine("security", "import", g4, "-k", outKeychainFilePath) }
            execOperations.exec {
                commandLine("curl", "-o", g2, "https://www.apple.com/certificateauthority/DeveloperIDG2CA.cer")
                standardOutput = curlOutput
            }
            execOperations.exec { commandLine("security", "import", g2, "-k", outKeychainFilePath) }
        }
        curlOutput.close()


        // 把我们的新 keychain 加到系统搜索路径中
        kotlin.run {
            val keychainsOutput = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("security", "list-keychains", "-d", "user")
                standardOutput = keychainsOutput
            }
            val existingUserKeys = keychainsOutput.toString().split('\n').map { it.trim('"', ' ') } // "
                // 如果我们已经有了, 需要过滤掉
                .filter { it != outKeychainFilePath && it.isNotEmpty() && it.isNotBlank() }

            val combinedKeys = buildList {
                addAll(existingUserKeys)
                add(outKeychainFilePath)
            }.toTypedArray()

            execOperations.exec {
                commandLine("security", "list-keychains", "-d", "user", "-s", *combinedKeys)
            }
            keychainsOutput.close()
        }

        // 立刻解锁 keychain
        execOperations.exec { commandLine("security", "unlock-keychain", "-p", keychainPassword, outKeychainFilePath) }

        kotlin.run {
            val identityOutput = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("security", "find-identity", "-v", "-p", "codesigning", outKeychainFilePath)
                standardOutput = identityOutput
            }
            val identities = identityOutput.toString()
            if (!identities.contains("Apple Distribution") && !identities.contains("iOS Distribution")) {
                throw GradleException(
                    "No distribution signing identity found in build keychain. " +
                            "APPLE_DISTRIBUTION_PRIVATE_KEY_PFX must include a certificate+private-key bundle (typically .p12), not CSR/private key only.",
                )
            }
            identityOutput.close()
        }

        logger.lifecycle("Prepared ephemeral signing keychain at $outKeychainFilePath")
    }
}

val buildDebugArchive = tasks.register("buildDebugArchive", XcodeArchiveTask::class) {
    group = "build"
    description = "Builds the iOS framework for Debug"
    dependsOn(":app:shared:application:linkPodDebugFrameworkIosArm64")

    workingDirectory = layout.projectDirectory
    workspaceDirectory = layout.projectDirectory.dir("Animeko.xcworkspace")
    scheme = "Animeko"
    destination = "generic/platform=iOS"
    sdk = "iphoneos"
    archiveConfiguration = "Debug"
    codeSigningAllowed = false
    codeSigningRequired = false
    archivePath = layout.buildDirectory.dir("archives/debug/Animeko.xcarchive")
}

val buildReleaseArchive = tasks.register("buildReleaseArchive", XcodeArchiveTask::class) {
    group = "build"
    description = "Builds the iOS framework for Release"
    dependsOn(":app:shared:application:linkPodReleaseFrameworkIosArm64")

    workingDirectory = layout.projectDirectory
    workspaceDirectory = layout.projectDirectory.dir("Animeko.xcworkspace")
    scheme = "Animeko"
    destination = "generic/platform=iOS"
    sdk = "iphoneos"
    archiveConfiguration = "Release"
    codeSigningAllowed = false
    codeSigningRequired = false
    archivePath = layout.buildDirectory.dir("archives/release/Animeko.xcarchive")
}


/**
 * Packages an unsigned IPA **and** injects an ad‑hoc signature so sideloaders can re‑sign it.
 *
 * This task is **configuration‑cache safe** – it does *not* capture the `Project` instance.
 */
@CacheableTask
abstract class BuildUnsignedIpaTask : DefaultTask() {

    /* -------------------------------------------------------------
     * Inputs / outputs
     * ----------------------------------------------------------- */

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveDir: DirectoryProperty

    @get:OutputFile
    abstract val outputIpa: RegularFileProperty

    /* -------------------------------------------------------------
     * Services (injected)
     * ----------------------------------------------------------- */

    @get:Inject
    abstract val execOperations: ExecOperations

    /* -------------------------------------------------------------
     * Action
     * ----------------------------------------------------------- */

    @TaskAction
    fun buildIpa() {
        // 1. Locate the .app inside the .xcarchive
        val appDir = archiveDir.get().asFile.resolve("Products/Applications/Animeko.app")
        if (!appDir.exists())
            throw GradleException("Could not find Animeko.app in archive at: ${appDir.absolutePath}")

        // 2. Create temporary Payload directory and copy .app into it
        val payloadDir = File(temporaryDir, "Payload").apply { mkdirs() }
        val destApp = File(payloadDir, appDir.name)
        appDir.copyRecursively(destApp, overwrite = true)

        // 3. Inject placeholder (ad‑hoc) code signature so AltStore / SideStore accept it
        logger.lifecycle("[IPA] Ad‑hoc signing ${destApp.name} …")
        execOperations.exec {
            commandLine(
                "codesign", "--force", "--deep", "--sign", "-", "--timestamp=none",
                destApp.absolutePath,
            )
        }

        // 4. Zip Payload ⇒ .ipa using the system `zip` command
        //
        //    -r : recurse into directories
        //    -y : store symbolic links as the link instead of the referenced file
        //
        // The working directory is the temporary folder so the archive
        // has a top‑level "Payload/" directory (required for .ipa files).
        val zipFile = File(temporaryDir, "Animeko.zip")
        execOperations.exec {
            workingDir(temporaryDir)
            commandLine("zip", "-r", "-y", zipFile.absolutePath, "Payload")
        }

        // 5. Move to final location (with .ipa extension)
        outputIpa.get().asFile.apply {
            parentFile.mkdirs()
            delete()
            zipFile.renameTo(this)
        }

        logger.lifecycle("[IPA] Created ad‑hoc‑signed IPA at: ${outputIpa.get().asFile.absolutePath}")
    }
}


tasks.register("buildDebugIpa", BuildUnsignedIpaTask::class) {
    description = "Manually packages the .app from the .xcarchive into an unsigned .ipa"
    group = "build"

    // Adjust these paths as needed
    archiveDir = layout.buildDirectory.dir("archives/debug/Animeko.xcarchive")
    outputIpa = layout.buildDirectory.file("archives/debug/Animeko.ipa")
    dependsOn(buildDebugArchive)
}

tasks.register("buildReleaseIpa", BuildUnsignedIpaTask::class) {
    description = "Manually packages the .app from the .xcarchive into an unsigned .ipa"
    group = "build"

    // Adjust these paths as needed
    archiveDir = layout.buildDirectory.dir("archives/release/Animeko.xcarchive")
    outputIpa = layout.buildDirectory.file("archives/release/Animeko.ipa")
    dependsOn(buildReleaseArchive)
}

// --- App Store signed build for TestFlight ---

@DisableCachingByDefault(because = "Consumes signing secrets and writes provisioning profiles to the user home directory")
abstract class InstallProvisioningProfileTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val provisioningProfileBase64: Property<String>

    @get:OutputDirectory
    abstract val provisioningProfilesDir: DirectoryProperty

    @TaskAction
    fun install() {
        val profileBase64 = provisioningProfileBase64.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPSTORE_PROVISIONING_PROFILE is not set.")
        val profilesDir = provisioningProfilesDir.get().asFile.apply { mkdirs() }

        val profile = ProvisioningProfileUtils.decode(profileBase64)
        val profileFile = profilesDir.resolve("${profile.uuid}.mobileprovision")
        profileFile.writeBytes(profile.decoded)
        logger.lifecycle("Installed provisioning profile: ${profileFile.absolutePath} (UUID=${profile.uuid}, Name=${profile.name})")
    }
}

@DisableCachingByDefault(because = "Generates exportOptions.plist from template and signing secrets")
abstract class PatchExportOptionsPlistTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templateFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val profileName: Property<String>

    @get:Input
    @get:Optional
    abstract val teamId: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun patch() {
        val resolvedTeamId = teamId.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPLE_DEVELOPER_TEAM_ID is not set")
        val resolvedProfileName = profileName.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Could not resolve provisioning profile Name from APPSTORE_PROVISIONING_PROFILE")
        val text = templateFile.get().asFile.readText()
        val updated = text
            .replace(
                Regex("""(<key>teamID</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$resolvedTeamId$3",
            )
            .replace(
                Regex("""(<key>org\.animeko\.animeko</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$resolvedProfileName$3",
            )

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(updated)
        }
        logger.lifecycle("Generated exportOptions.plist with teamId=$resolvedTeamId, profileName=$resolvedProfileName")
    }
}

@DisableCachingByDefault(because = "Runs xcodebuild exportArchive against local Apple toolchain")
abstract class XcodeExportArchiveTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workingDirectory: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archivePath: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val exportOptionsPlist: RegularFileProperty

    @get:OutputDirectory
    abstract val exportPath: DirectoryProperty

    @get:OutputFile
    abstract val outputIpa: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun exportIpa() {
        execOperations.exec {
            workingDir(workingDirectory.get().asFile)
            commandLine(
                "xcodebuild",
                "-exportArchive",
                "-archivePath", archivePath.get().asFile.absolutePath,
                "-exportPath", exportPath.get().asFile.absolutePath,
                "-exportOptionsPlist", exportOptionsPlist.get().asFile.absolutePath,
            )
        }
        val ipaFile = outputIpa.get().asFile
        if (!ipaFile.exists()) {
            throw GradleException("Signed IPA was not produced at expected path: ${ipaFile.absolutePath}")
        }
    }
}

val appleDeveloperTeamId = providers.provider {
    project.getPropertyOrNull("APPLE_DEVELOPER_TEAM_ID").orEmpty()
}
val appStoreProvisioningProfile = providers.provider {
    project.getPropertyOrNull("APPSTORE_PROVISIONING_PROFILE").orEmpty()
}
val appleDistributionPrivateKeyPfx = providers.provider {
    project.getPropertyOrNull("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX").orEmpty()
}
val appleDistributionPrivateKeyPfxPassword = providers.provider {
    project.getPropertyOrNull("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX_IMPORT_PWD").orEmpty()
}
val appStoreProvisioningProfileName = appStoreProvisioningProfile.map {
    ProvisioningProfileUtils.decode(it).name
}
val provisioningProfilesDirProvider = providers.systemProperty("user.home")
    .map { File(it, "Library/MobileDevice/Provisioning Profiles") }

val installProvisioningProfile = tasks.register("installProvisioningProfile", InstallProvisioningProfileTask::class) {
    group = "build"
    description = "Decodes and installs the provisioning profile for App Store distribution"
    provisioningProfileBase64.convention(appStoreProvisioningProfile)
    provisioningProfilesDir = layout.dir(provisioningProfilesDirProvider)
}

val prepareBuildKeychain = tasks.register("prepareBuildKeychain", PrepareBuildKeychainTask::class) {
    group = "build"
    description = "Creates an ephemeral build keychain and imports Apple Distribution signing key material"
    privateKeyPfxBase64.convention(appleDistributionPrivateKeyPfx)
    privateKeyPfxPassword.convention(appleDistributionPrivateKeyPfxPassword)
    outKeychain = layout.buildDirectory.file("release-animeko.keychain")
}

val buildSignedReleaseArchive = tasks.register("buildSignedReleaseArchive", XcodeArchiveTask::class) {
    group = "build"
    description = "Builds a signed iOS archive for App Store distribution"
    dependsOn(":app:shared:application:linkPodReleaseFrameworkIosArm64", prepareBuildKeychain)

    workingDirectory = layout.projectDirectory
    workspaceDirectory = layout.projectDirectory.dir("Animeko.xcworkspace")
    scheme = "Animeko"
    destination = "generic/platform=iOS"
    sdk = "iphoneos"
    archiveConfiguration = "Release"
    codeSigningAllowed = true
    codeSigningRequired = true
    codeSignStyle = "Manual"
    profileName.convention(appStoreProvisioningProfileName)
    developmentTeamId.convention(appleDeveloperTeamId)
    signingKeychain = layout.buildDirectory.file("release-animeko.keychain").get().asFile.absolutePath
    archivePath = layout.buildDirectory.dir("archives/release-signed/Animeko.xcarchive")
}

val patchExportOptionsPlist = tasks.register("patchExportOptionsPlist", PatchExportOptionsPlistTask::class) {
    group = "build"
    dependsOn(installProvisioningProfile)

    templateFile = layout.projectDirectory.file("Animeko/exportOptions.plist.template.txt")
    profileName.convention(appStoreProvisioningProfileName)
    outputFile = layout.buildDirectory.file("export/exportOptions.plist")
    teamId.convention(appleDeveloperTeamId)
}

tasks.register("buildSignedReleaseIpa", XcodeExportArchiveTask::class) {
    group = "build"
    description = "Exports a signed IPA for App Store / TestFlight distribution"
    dependsOn(buildSignedReleaseArchive, patchExportOptionsPlist)

    workingDirectory = layout.projectDirectory
    archivePath = layout.buildDirectory.dir("archives/release-signed/Animeko.xcarchive")
    exportPath = layout.buildDirectory.dir("archives/release-signed/export")
    exportOptionsPlist = layout.buildDirectory.file("export/exportOptions.plist")
    outputIpa = layout.buildDirectory.file("archives/release-signed/export/Animeko.ipa")
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
    val command = buildList {
        addAll(
            ipaArguments(
                destination = destination,
                sdk = "iphonesimulator",
            ),
        )
        add("-configuration")
        add("Debug")
        add("build")
    }
    commandLine(command)
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

    commandLine("xcrun", "simctl", "launch", "booted", "org.animeko.animeko")
}

val patchInfoPlist = tasks.register("patchInfoPlist", Task::class) {
    group = "run"
    description = "Patches Info.plist"

    val versionName = project.property("version.name").toString().substringBefore("-")
    inputs.property("version.name", versionName)
    val versionCode = project.property("android.version.code")
    inputs.property("version.code", versionCode)

    val templateFile = file("Animeko/Info.plist.template.txt")
    val outputFile = file("Animeko/Info.plist")
    inputs.file(templateFile)
    outputs.file(outputFile)

    doLast {
        val text = templateFile.readText()

        // Replace CFBundleShortVersionString with versionName
        // and CFBundleVersion with versionCode using a simple regex
        val updated = text
            .replace(
                Regex("""(<key>CFBundleShortVersionString</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$versionName$3",
            )
            .replace(
                Regex("""(<key>CFBundleVersion</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$versionCode$3",
            )

        if (updated != text) {
            outputFile.writeText(updated)
            logger.lifecycle("Patched Info.plist with versionName=$versionName and versionCode=$versionCode")
        } else {
            logger.lifecycle("No changes needed or did not match expected patterns in Info.plist.")
        }
    }
}

tasks.matching { it.path == ":app:shared:application:embedAndSignPodAppleFrameworkForXcode" }.configureEach {
    dependsOn(patchInfoPlist)
    inputs.file(file("Animeko/Info.plist"))
}
