/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UnstableApiUsage")

import org.gradle.api.GradleException
import org.gradle.api.tasks.bundling.Zip
import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.atomicfu)
}

fun ciProperty(name: String): Provider<String> {
    return providers.provider { getPropertyOrNull(name) }
}

val ciTag = ciProperty("CI_TAG").orElse("4.9.0-dev")
val ciReleaseFullVersion = ciTag.map(ReleaseArtifactNames::fullVersionFromTag)
val ciReleaseId = ciProperty("CI_RELEASE_ID")
val ciGithubRepository = ciProperty("GITHUB_REPOSITORY")
val ciGithubToken = ciProperty("GITHUB_TOKEN")
val ciUploadToS3 = ciProperty("UPLOAD_TO_S3").map { it.equals("true", ignoreCase = true) }.orElse(false)
val ciAwsAccessKeyId = ciProperty("AWS_ACCESS_KEY_ID")
val ciAwsSecretAccessKey = ciProperty("AWS_SECRET_ACCESS_KEY")
val ciAwsBaseUrl = ciProperty("AWS_BASEURL")
val ciAwsRegion = ciProperty("AWS_REGION")
val ciAwsBucket = ciProperty("AWS_BUCKET")
val githubRef = ciProperty("GITHUB_REF")
val githubSha = ciProperty("GITHUB_SHA")
val appStoreApiKeyId = ciProperty("APPSTORE_API_KEY_ID")
val appStoreApiPrivateKey = ciProperty("APPSTORE_API_PRIVATE_KEY")
val appStoreIssuerId = ciProperty("APPSTORE_ISSUER_ID")
val userHome = providers.environmentVariable("HOME").orElse(providers.systemProperty("user.home"))

fun ReleaseUploadTask.configureReleaseUploadInputs() {
    releaseTag.convention(ciTag)
    releaseFullVersion.convention(ciReleaseFullVersion)
    releaseId.convention(ciReleaseId)
    githubRepository.convention(ciGithubRepository)
    githubToken.convention(ciGithubToken)
    uploadToS3.convention(ciUploadToS3)
    awsAccessKeyId.convention(ciAwsAccessKeyId)
    awsSecretAccessKey.convention(ciAwsSecretAccessKey)
    awsBaseUrl.convention(ciAwsBaseUrl)
    awsRegion.convention(ciAwsRegion)
    awsBucket.convention(ciAwsBucket)
}

tasks.register("uploadAndroidApk", UploadAndroidApksTask::class) {
    configureReleaseUploadInputs()
    apkDirectory.set(project(":app:android").layout.buildDirectory.dir("outputs/apk/default/release"))
}

val uploadAndroidApkGithubQr = tasks.register("uploadAndroidApkGithubQr", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(rootProject.layout.projectDirectory.file("apk-qrcode-github.png"))
    assetContentType.set("image/png")
    assetName.set(
        ciReleaseFullVersion.map { version ->
            ReleaseArtifactNames.androidAppQr(version, "universal", "github")
        },
    )
}

val uploadAndroidApkCloudflareQr = tasks.register("uploadAndroidApkCloudflareQr", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(rootProject.layout.projectDirectory.file("apk-qrcode-cloudflare.png"))
    assetContentType.set("image/png")
    assetName.set(
        ciReleaseFullVersion.map { version ->
            ReleaseArtifactNames.androidAppQr(version, "universal", "cloudflare")
        },
    )
}

tasks.register("uploadAndroidApkQR") {
    dependsOn(uploadAndroidApkGithubQr, uploadAndroidApkCloudflareQr)
}

val uploadIosIpaGithubQr = tasks.register("uploadIosIpaGithubQr", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(rootProject.layout.projectDirectory.file("ipa-qrcode-github.png"))
    assetContentType.set("image/png")
    assetName.set(
        ciReleaseFullVersion.map { version ->
            ReleaseArtifactNames.iosIpaQr(version, "github")
        },
    )
}

val uploadIosIpaCloudflareQr = tasks.register("uploadIosIpaCloudflareQr", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(rootProject.layout.projectDirectory.file("ipa-qrcode-cloudflare.png"))
    assetContentType.set("image/png")
    assetName.set(
        ciReleaseFullVersion.map { version ->
            ReleaseArtifactNames.iosIpaQr(version, "cloudflare")
        },
    )
}

tasks.register("uploadIosIpaQR") {
    dependsOn(uploadIosIpaGithubQr, uploadIosIpaCloudflareQr)
}

val zipDesktopDistribution = tasks.register("zipDesktopDistribution", Zip::class) {
    dependsOn(":app:desktop:createReleaseDistributable")
    from(project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main-release/app"))
    archiveBaseName.set("ani")
    archiveVersion.set(ciReleaseFullVersion)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveExtension.set("zip")
}

tasks.register("uploadDesktopInstallers", UploadDesktopInstallersTask::class) {
    configureReleaseUploadInputs()
    when (currentReleaseHostOs()) {
        ReleaseHostOs.WINDOWS -> {
            dependsOn(zipDesktopDistribution)
            zipDistribution.set(zipDesktopDistribution.flatMap { it.archiveFile })
        }

        ReleaseHostOs.MACOS -> {
            if (currentReleaseHostArch() == "aarch64") {
                dependsOn(":app:desktop:packageReleaseDistributionForCurrentOS")
                binaryDirectory.set(project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main-release"))
            } else {
                dependsOn(zipDesktopDistribution)
                zipDistribution.set(zipDesktopDistribution.flatMap { it.archiveFile })
            }
        }

        ReleaseHostOs.LINUX -> {
            linuxAppImage.set(rootProject.layout.projectDirectory.file("Animeko-x86_64.AppImage"))
            linuxAppImageZsync.set(rootProject.layout.projectDirectory.file("Animeko-x86_64.AppImage.zsync"))
        }
    }
}

tasks.register("uploadIosIpa", UploadReleaseAssetTask::class) {
    configureReleaseUploadInputs()
    artifactFile.set(project(":app:ios").layout.buildDirectory.file("archives/release/Animeko.ipa"))
    assetContentType.set("application/x-iphone")
    assetName.set(ciReleaseFullVersion.map(ReleaseArtifactNames::iosIpa))
}

val appStoreConnectKeyFile = userHome.zip(appStoreApiKeyId) { home, apiKeyId ->
    File(home).resolve("private_keys/AuthKey_${apiKeyId}.p8")
}

val prepareAppStoreConnectKey = tasks.register("prepareAppStoreConnectKey", PrepareAppStoreConnectApiKeyTask::class) {
    apiKeyId.convention(appStoreApiKeyId)
    apiPrivateKey.convention(appStoreApiPrivateKey)
    outputKeyFile.set(layout.file(appStoreConnectKeyFile))
}

tasks.register("uploadAppStoreConnectTestflight", UploadAppStoreConnectTestflightTask::class) {
    dependsOn(prepareAppStoreConnectKey)
    ipaFile.set(project(":app:ios").layout.buildDirectory.file("archives/release-signed/export/Animeko.ipa"))
    workingDirectory.set(project(":app:ios").layout.projectDirectory)
    apiKeyId.convention(appStoreApiKeyId)
    apiIssuerId.convention(appStoreIssuerId)
    apiPrivateKeyFile.set(layout.file(appStoreConnectKeyFile))
}

tasks.register("prepareArtifactsForManualUpload") {
    dependsOn(
        ":app:desktop:createReleaseDistributable",
        ":app:desktop:packageReleaseDistributionForCurrentOS",
        zipDesktopDistribution,
    )

    doLast {
        val distributionDir = layout.buildDirectory.dir("distribution").get().asFile.apply { mkdirs() }
        val releaseVersion = project.version.toString()

        fun copyToDistribution(name: String, file: File) {
            val target = distributionDir.resolve(name)
            target.delete()
            file.copyTo(target)
            println("File written: ${target.absoluteFile}")
        }

        fun copyBinary(
            kind: String,
            osName: String,
            archName: String = currentReleaseHostArch(),
        ) {
            val source = project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main-release/$kind")
                .get().asFile
                .walk()
                .single { it.isFile && it.extension == kind }

            copyToDistribution(
                name = ReleaseArtifactNames.desktopDistributionFile(releaseVersion, osName, archName, kind),
                file = source,
            )
        }

        when (currentReleaseHostOs()) {
            ReleaseHostOs.WINDOWS -> copyToDistribution(
                name = ReleaseArtifactNames.desktopDistributionFile(releaseVersion, "windows", extension = "zip"),
                file = zipDesktopDistribution.get().archiveFile.get().asFile,
            )

            ReleaseHostOs.MACOS -> {
                if (currentReleaseHostArch() == "x86_64") {
                    copyToDistribution(
                        name = ReleaseArtifactNames.desktopDistributionFile(releaseVersion, "macos", extension = "zip"),
                        file = zipDesktopDistribution.get().archiveFile.get().asFile,
                    )
                } else {
                    copyBinary("dmg", osName = "macos")
                }
            }

            ReleaseHostOs.LINUX -> copyToDistribution(
                name = ReleaseArtifactNames.desktopDistributionFile(
                    releaseVersion,
                    "linux",
                    "x86_64",
                    extension = "appimage",
                ),
                file = rootProject.file("Animeko-x86_64.AppImage"),
            )
        }

        copyToDistribution(
            name = ReleaseArtifactNames.desktopDistributionFile(
                releaseVersion,
                osName = currentReleaseHostOs().name.lowercase(),
                extension = "zip",
            ),
            file = zipDesktopDistribution.get().archiveFile.get().asFile,
        )
    }
}

val gradleProperties = rootProject.file("gradle.properties")

tasks.register("updateDevVersionNameFromGit") {
    doLast {
        val ref = githubRef.orNull ?: throw GradleException("GITHUB_REF is not provided.")
        val sha = githubSha.orNull ?: throw GradleException("GITHUB_SHA is not provided.")
        val propertiesText = gradleProperties.readText()
        val baseVersion = (
            Regex("version.name=(.+)").find(propertiesText)
                ?: error("Failed to find base version. Check version.name in gradle.properties")
            )
            .groupValues[1]
            .substringBefore("-")
        val branch = ref.substringAfterLast("/")
        val newVersion = "$baseVersion-$branch-${sha.take(8)}"
        println("New version name: $newVersion")
        gradleProperties.writeText(
            propertiesText.replaceFirst(Regex("version.name=(.+)"), "version.name=$newVersion"),
        )
    }
}

tasks.register("updateReleaseVersionNameFromGit") {
    doLast {
        val releaseVersion = ReleaseArtifactNames.fullVersionFromTag(ciTag.get())
        val releaseVersionCode = ReleaseArtifactNames.versionCodeFromTag(ciTag.get())
        val propertiesText = gradleProperties.readText()
        println("New version: $releaseVersion($releaseVersionCode)")
        gradleProperties.writeText(
            propertiesText
                .replaceFirst(Regex("version.name=(.+)"), "version.name=$releaseVersion")
                .replaceFirst(Regex("ios.version.code=(.+)"), "ios.version.code=$releaseVersionCode"),
            // 不要更新 version.code, 这是为了让更新到测试版出 bug 的人可以回退到旧版
        )
    }
}
