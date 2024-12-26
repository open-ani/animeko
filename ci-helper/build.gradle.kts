/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("UnstableApiUsage")

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.cio.readChannel
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.internal.impldep.com.amazonaws.auth.AWSStaticCredentialsProvider
import org.gradle.internal.impldep.com.amazonaws.auth.BasicAWSCredentials
import org.gradle.internal.impldep.com.amazonaws.client.builder.AwsClientBuilder
import org.gradle.internal.impldep.com.amazonaws.services.s3.AmazonS3ClientBuilder
import org.gradle.internal.impldep.com.amazonaws.services.s3.model.ObjectMetadata
import org.gradle.internal.impldep.com.amazonaws.services.s3.model.PutObjectRequest
import java.security.MessageDigest

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.atomicfu")
}

val hostOS: OS by lazy {
    when {
        Os.isFamily(Os.FAMILY_WINDOWS) -> OS.WINDOWS
        Os.isFamily(Os.FAMILY_MAC) -> OS.MACOS
        Os.isFamily(Os.FAMILY_UNIX) -> OS.LINUX
        else -> error("Unsupported OS: ${System.getProperty("os.name")}")
    }
}

val hostArch: String by lazy {
    when (val arch = System.getProperty("os.arch")) {
        "x86_64" -> "x86_64"
        "amd64" -> "x86_64"
        "arm64" -> "aarch64"
        "aarch64" -> "aarch64"
        else -> error("Unsupported host architecture: $arch")
    }
}


enum class OS(
    val isUnix: Boolean,
) {
    WINDOWS(false),
    MACOS(true),
    LINUX(true),
}


val namer = ArtifactNamer()

class ArtifactNamer {
    private val APP_NAME = "ani"

    fun getFullVersionFromTag(tag: String): String {
        return tag.substringAfter("v")
    }

    // fullVersion example: 2.0.0-beta03
    fun androidApp(fullVersion: String, arch: String): String {
        return "$APP_NAME-$fullVersion-$arch.apk"
    }

    fun androidAppQR(fullVersion: String, arch: String, server: String): String {
        return "${androidApp(fullVersion, arch)}.$server.qrcode.png"
    }

    // Ani-2.0.0-beta03-macos-amd64.dmg
    // Ani-2.0.0-beta03-macos-arm64.dmg
    // Ani-2.0.0-beta03-windows-amd64.msi
    // Ani-2.0.0-beta03-debian-amd64.deb
    // Ani-2.0.0-beta03-redhat-amd64.rpm
    fun desktopDistributionFile(
        fullVersion: String,
        osName: String,
        archName: String = hostArch,
        extension: String
    ): String {
        return "$APP_NAME-$fullVersion-$osName-$archName.$extension"
    }

    fun server(fullVersion: String, extension: String): String {
        return "$APP_NAME-server-$fullVersion.$extension"
    }
}

tasks.register("uploadAndroidApk") {
    val buildDirectory = project(":app:android").layout.buildDirectory
    doLast {
        ReleaseEnvironment().run {
            val files = buildDirectory.file("outputs/apk/release")
                .get().asFile.walk()
                .filter { it.extension == "apk" && it.name.contains("release") }

            for (file in files) {
                // android-arm64-v8a-release.apk
                val arch = file.name.substringAfter("android-")
                    .substringBefore("-release.apk")
                uploadReleaseAsset(
                    name = namer.androidApp(fullVersion, arch),
                    contentType = "application/vnd.android.package-archive",
                    file = file,
                )
            }
        }
    }
}

tasks.register("uploadAndroidApkQR") {
    doLast {
        ReleaseEnvironment().run {
            uploadReleaseAsset(
                name = namer.androidAppQR(fullVersion, "universal", "github"),
                contentType = "image/png",
                file = rootProject.file("apk-qrcode-github.png"),
            )
            uploadReleaseAsset(
                name = namer.androidAppQR(fullVersion, "universal", "cloudflare"),
                contentType = "image/png",
                file = rootProject.file("apk-qrcode-cloudflare.png"),
            )
        }
    }
}

val zipDesktopDistribution = tasks.register("zipDesktopDistribution", Zip::class) {
    dependsOn(
        ":app:desktop:createReleaseDistributable",
    )
    from(project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main-release/app"))
    // ani-3.0.0-beta22-dev7.zip
    archiveBaseName.set("ani")
    archiveVersion.set(ReleaseEnvironment().fullVersion)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveExtension.set("zip")
}

tasks.register("uploadDesktopInstallers") {
    dependsOn(zipDesktopDistribution)

    if (hostOS != OS.WINDOWS) {
        dependsOn(
            ":app:desktop:packageReleaseDistributionForCurrentOS",
        )
    }

    doLast {
        ReleaseEnvironment().uploadDesktopDistributions()
    }
}

//tasks.register("uploadServerDistribution") {
//    dependsOn(
//        ":server:distZip",
//        ":server:distTar",
//    )
//
//    doLast {
//        val distZip = project(":server").tasks.getByName("distZip", Zip::class).archiveFile.get().asFile
//        val distTar = project(":server").tasks.getByName("distTar", Tar::class).archiveFile.get().asFile
//
//        ReleaseEnvironment().run {
//            uploadReleaseAsset(namer.server(fullVersion, "tar"), "application/x-tar", distTar)
//            uploadReleaseAsset(namer.server(fullVersion, "zip"), "application/zip", distZip)
//        }
//    }
//}

tasks.register("prepareArtifactsForManualUpload") {
    dependsOn(
        ":app:desktop:createReleaseDistributable",
        ":app:desktop:packageReleaseDistributionForCurrentOS",
    )
    dependsOn(zipDesktopDistribution)

    doLast {
        val distributionDir = project.layout.buildDirectory.dir("distribution").get().asFile.apply { mkdirs() }

        object : ReleaseEnvironment() {
            override val fullVersion: String = project.version.toString()
            override fun uploadReleaseAsset(name: String, contentType: String, file: File) {
                val target = distributionDir.resolve(name)
                target.delete()
                file.copyTo(target)
                println("File written: ${target.absoluteFile}")
            }
        }.run {
            uploadDesktopDistributions()
            uploadReleaseAsset(
                name = namer.desktopDistributionFile(
                    fullVersion,
                    osName = hostOS.name.lowercase(),
                    extension = "zip",
                ),
                contentType = "application/octet-stream",
                file = zipDesktopDistribution.get().archiveFile.get().asFile,
            )
        }
    }
}

// do not use `object`, compiler bug
open class ReleaseEnvironment {
    private fun getProperty(name: String) =
        System.getProperty(name)
            ?: System.getenv(name)
            ?: properties[name]?.toString()
            ?: getLocalProperty(name)
            ?: ext.get(name).toString()

    // K2 IDE can't resolve it if it's top-level
    private fun findProperty(name: String) =
        System.getProperty(name)
            ?: System.getenv(name)
            ?: properties[name]?.toString()
            ?: getLocalProperty(name)
            ?: runCatching { ext.get(name) }.getOrNull()?.toString()

    private val tag: String by lazy {
        (findProperty("CI_TAG") ?: "3.0.0-dev").also { println("tag = $it") }
    }
    private val branch by lazy {
        getProperty("GITHUB_REF").substringAfterLast("/").also { println("branch = $it") }
    }
    private val shaShort by lazy {
        getProperty("GITHUB_SHA").take(8).also { println("shaShort = $it") }
    }
    open val fullVersion by lazy {
        namer.getFullVersionFromTag(tag).also { println("fullVersion = $it") }
    }
    val releaseId by lazy {
        getProperty("CI_RELEASE_ID").also { println("releaseId = $it") }
    }
    val repository by lazy {
        getProperty("GITHUB_REPOSITORY").also { println("repository = $it") }
    }
    val token by lazy {
        getProperty("GITHUB_TOKEN").also { println("token = ${it.isNotEmpty()}") }
    }

    open fun uploadReleaseAsset(
        name: String,
        contentType: String,
        file: File,
    ) {
        check(file.exists()) { "File '${file.absolutePath}' does not exist when attempting to upload '$name'." }
        val tag = getProperty("CI_TAG")
        val fullVersion = namer.getFullVersionFromTag(tag)
        val releaseId = getProperty("CI_RELEASE_ID")
        val repository = getProperty("GITHUB_REPOSITORY")
        val token = getProperty("GITHUB_TOKEN")
        println("tag = $tag")
        return uploadReleaseAsset(repository, releaseId, token, fullVersion, name, contentType, file)
    }

    private val s3Client by lazy {
        AmazonS3ClientBuilder
            .standard()
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(
                        getProperty("AWS_ACCESS_KEY_ID"),
                        getProperty("AWS_SECRET_ACCESS_KEY"),
                    ),
                ),
            )
            .apply {
                setEndpointConfiguration(
                    AwsClientBuilder.EndpointConfiguration(
                        getProperty("AWS_BASEURL"),
                        getProperty("AWS_REGION"),
                    ),
                )
            }
            .build()
    }

    fun uploadReleaseAsset(
        repository: String,
        releaseId: String,
        token: String,
        fullVersion: String,

        name: String,
        contentType: String,
        file: File,
    ) {
        println("fullVersion = $fullVersion")
        println("releaseId = $releaseId")
        println("token = ${token.isNotEmpty()}")
        println("repository = $repository")

        // Compute the SHA-1 for the file
        val sha1Checksum = computeSha1Checksum(file)
        val sha1FileName = "$name.sha1"
        // Write the checksum to a temporary file
        val sha1File = createTempSha1File(sha1Checksum, sha1FileName)

        runBlocking {
            HttpClient {
                expectSuccess = true
            }.use { client ->
                // 1) Upload the main file to GitHub
                uploadFileToGitHub(client, repository, releaseId, token, file, name, ContentType.parse(contentType))

                // 2) Upload the SHA-1 file to GitHub
                uploadFileToGitHub(client, repository, releaseId, token, sha1File, sha1FileName, ContentType.Text.Plain)

                // Optionally upload to S3
                if (getProperty("UPLOAD_TO_S3") == "true") {
                    putS3Object(name, file, contentType)
                    // Upload the sha1 file
                    putS3Object(sha1FileName, sha1File, "text/plain")
                }
            }
        }
    }

    /**
     * Upload a file as a release asset to the specified GitHub repository/release.
     *
     * @param client      An instance of [HttpClient] you manage (preferably use `.use { }`).
     * @param repository  The GitHub repository in "owner/repo" format (e.g. "open-ani/animeko").
     * @param releaseId   The numeric ID of the release (not the tag name).
     * @param token       Your GitHub personal access token (PAT) with the "repo" scope.
     * @param file        The [File] to upload.
     * @param fileName    How the file will appear on the release page.
     * @param contentType The MIME type (e.g. "application/vnd.android.package-archive" for `.apk`).
     *
     * @return `true` if the upload was successful; `false` if the server returned a 422
     *         indicating the asset already exists.
     */
    suspend fun uploadFileToGitHub(
        client: HttpClient,
        repository: String,
        releaseId: String,
        token: String,
        file: File,
        fileName: String,
        contentType: ContentType
    ): Boolean {
        return try {
            client.post("https://uploads.github.com/repos/$repository/releases/$releaseId/assets") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                parameter("name", fileName)

                contentType(contentType)
                setBody(
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentType: ContentType
                            get() = contentType
                        override val contentLength: Long
                            get() = file.length()

                        override fun readFrom(): ByteReadChannel = file.readChannel()
                    },
                )
            }

            // If we made it here, the upload is successful
            true
        } catch (e: ClientRequestException) {
            // Check for the 422 "already exists" error
            if (e.response.status.value == 422) {
                println("Asset already exists: $fileName")
                false
            } else {
                // Propagate other errors
                throw e
            }
        }
    }

    private fun putS3Object(name: String, file: File, contentType: String) {
        val request = PutObjectRequest(getProperty("AWS_BUCKET"), "$tag/$name", file).apply {
            this.metadata = ObjectMetadata().apply {
                this.contentType = contentType
            }
        }
        s3Client.putObject(request)
    }

    /**
     * Computes the SHA-1 checksum of the given file.
     */
    private fun computeSha1Checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        // Convert the byte array to a hex string
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Writes the given SHA-1 checksum to a temporary file.
     */
    private fun createTempSha1File(sha1Checksum: String, sha1FileName: String): File {
        // You can adjust the directory for the temp file if needed
        val tempFile = File.createTempFile(sha1FileName, null)
        tempFile.writeText(sha1Checksum)
        return tempFile
    }

    fun generateDevVersionName(
        base: String,
    ): String {
        return "$base-${branch}-${shaShort}"
    }

    fun generateReleaseVersionName(): String = tag.removePrefix("v")
}

fun ReleaseEnvironment.uploadDesktopDistributions() {
    fun uploadBinary(
        kind: String,

        osName: String,
        archName: String = hostArch,
    ) {
        uploadReleaseAsset(
            name = namer.desktopDistributionFile(
                fullVersion,
                osName,
                archName,
                extension = kind,
            ),
            contentType = "application/octet-stream",
            file = project(":app:desktop").layout.buildDirectory.dir("compose/binaries/main-release/$kind").get().asFile
                .walk()
                .single { it.extension == kind },
        )
    }
    // installers
    when (hostOS) {
        OS.WINDOWS -> {
            uploadReleaseAsset(
                name = namer.desktopDistributionFile(
                    fullVersion,
                    osName = hostOS.name.lowercase(),
                    extension = "zip",
                ),
                contentType = "application/x-zip",
                file = layout.buildDirectory.dir("distributions").get().asFile.walk().single { it.extension == "zip" },
            )
        }

        OS.MACOS -> {
            uploadBinary("dmg", osName = "macos")
        }

        OS.LINUX -> {
            uploadBinary("deb", osName = "debian")
            uploadBinary("rpm", osName = "redhat")
        }
    }
}

// ./gradlew updateDevVersionNameFromGit -DGITHUB_REF=refs/heads/master -DGITHUB_SHA=123456789 --no-configuration-cache
val gradleProperties = rootProject.file("gradle.properties")
tasks.register("updateDevVersionNameFromGit") {
    doLast {
        val properties = file(gradleProperties).readText()
        val baseVersion =
            (Regex("version.name=(.+)").find(properties)
                ?: error("Failed to find base version. Check version.name in gradle.properties"))
                .groupValues[1]
                .substringBefore("-")
        val new = ReleaseEnvironment().generateDevVersionName(base = baseVersion)
        println("New version name: $new")
        file(gradleProperties).writeText(
            properties.replaceFirst(Regex("version.name=(.+)"), "version.name=$new"),
        )
    }
}

// ./gradlew updateReleaseVersionNameFromGit -DGITHUB_REF=refs/heads/master -DGITHUB_SHA=123456789 --no-configuration-cache
tasks.register("updateReleaseVersionNameFromGit") {
    doLast {
        val properties = file(gradleProperties).readText()
        val new = ReleaseEnvironment().generateReleaseVersionName()
        println("New version name: $new")
        file(gradleProperties).writeText(
            properties.replaceFirst(Regex("version.name=(.+)"), "version.name=$new"),
        )
    }
}
