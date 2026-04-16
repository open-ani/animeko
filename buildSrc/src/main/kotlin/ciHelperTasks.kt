/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

object ReleaseArtifactNames {
    private const val appName = "ani"

    fun fullVersionFromTag(tag: String): String = tag.removePrefix("v")

    // alpha 只能又 6 个版本，beta 只能有 3 个版本
    fun versionCodeFromTag(tag: String): String {
        val match = Regex("""^v(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)(\d+))?$""").matchEntire(tag)
            ?: throw GradleException("Unsupported tag format: '$tag'")

        val major = match.groupValues[1].toIntOrNull()
        val minor = match.groupValues[2].toIntOrNull()
        val patch = match.groupValues[3].toIntOrNull()
        val channel = match.groupValues[4]
        val meta = match.groupValues[5].toIntOrNull()

        require(major != null && major in 0..9) {
            "Major version '$major' in tag '$tag' cannot be encoded into a single digit."
        }
        require(minor != null && minor in 0..99) {
            "Minor version '$minor' in tag '$tag' cannot be encoded into two digits."
        }
        require(patch != null && patch in 0..9) {
            "Patch version '$patch' in tag '$tag' cannot be encoded into a single digit."
        }

        val metaDigit = when (channel) {
            "alpha" -> when (meta) {
                1 -> 1
                2 -> 2
                3 -> 3
                4 -> 3
                5 -> 4
                else -> 6
            }

            "beta" -> when (meta) {
                1 -> 7
                2 -> 8
                else -> 9
            }

            else -> 0
        }

        return buildString(5) {
            append(major)
            append(minor.toString().padStart(2, '0'))
            append(patch)
            append(metaDigit)
        }
    }

    fun androidApp(fullVersion: String, arch: String): String = "$appName-$fullVersion-$arch.apk"

    fun androidAppQr(fullVersion: String, arch: String, server: String): String =
        "${androidApp(fullVersion, arch)}.$server.qrcode.png"

    fun iosIpa(fullVersion: String): String = "$appName-$fullVersion.ipa"

    fun iosIpaQr(fullVersion: String, server: String): String = "${iosIpa(fullVersion)}.$server.qrcode.png"

    fun desktopDistributionFile(
        fullVersion: String,
        osName: String,
        archName: String = currentReleaseHostArch(),
        extension: String,
    ): String = "$appName-$fullVersion-$osName-$archName.$extension"

    fun server(fullVersion: String, extension: String): String = "$appName-server-$fullVersion.$extension"
}

enum class ReleaseHostOs {
    WINDOWS,
    MACOS,
    LINUX,
}

fun currentReleaseHostOs(): ReleaseHostOs =
    when (getOs()) {
        Os.Windows -> ReleaseHostOs.WINDOWS
        Os.MacOS -> ReleaseHostOs.MACOS
        Os.Linux -> ReleaseHostOs.LINUX
        Os.Unknown -> throw GradleException("Unsupported OS: ${System.getProperty("os.name")}")
    }

fun currentReleaseHostArch(): String =
    when (getArch()) {
        Arch.X86_64 -> "x86_64"
        Arch.AARCH64 -> "aarch64"
    }

abstract class ReleaseUploadTask : DefaultTask() {
    @get:Input
    abstract val releaseTag: Property<String>

    @get:Input
    abstract val releaseFullVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseId: Property<String>

    @get:Input
    @get:Optional
    abstract val githubRepository: Property<String>

    @get:Input
    @get:Optional
    abstract val githubToken: Property<String>

    @get:Input
    abstract val uploadToS3: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val awsAccessKeyId: Property<String>

    @get:Input
    @get:Optional
    abstract val awsSecretAccessKey: Property<String>

    @get:Input
    @get:Optional
    abstract val awsBaseUrl: Property<String>

    @get:Input
    @get:Optional
    abstract val awsRegion: Property<String>

    @get:Input
    @get:Optional
    abstract val awsBucket: Property<String>

    protected fun uploadReleaseAsset(
        name: String,
        contentType: String,
        file: File,
    ) {
        check(file.isFile) {
            "File '${file.absolutePath}' does not exist when attempting to upload '$name'."
        }

        val releaseId = requireConfigured(releaseId, "CI_RELEASE_ID")
        val repository = requireConfigured(githubRepository, "GITHUB_REPOSITORY")
        val token = requireConfigured(githubToken, "GITHUB_TOKEN")
        val tag = releaseTag.get()

        logger.lifecycle("tag = $tag")
        logger.lifecycle("fullVersion = ${releaseFullVersion.get()}")
        logger.lifecycle("releaseId = $releaseId")
        logger.lifecycle("repository = $repository")
        logger.lifecycle("token = ${token.isNotEmpty()}")

        val sha1File = File.createTempFile("release-asset-", ".sha1").apply {
            writeText(computeSha1Checksum(file))
        }
        val sha1FileName = "$name.sha1"

        try {
            runBlocking {
                HttpClient(OkHttp) {
                    expectSuccess = true
                }.use { client ->
                    uploadFileToGitHub(
                        client = client,
                        repository = repository,
                        releaseId = releaseId,
                        token = token,
                        file = file,
                        fileName = name,
                        contentType = ContentType.parse(contentType),
                    )
                    uploadFileToGitHub(
                        client = client,
                        repository = repository,
                        releaseId = releaseId,
                        token = token,
                        file = sha1File,
                        fileName = sha1FileName,
                        contentType = ContentType.Text.Plain,
                    )

                    if (uploadToS3.get()) {
                        putS3Object(tag, name, file, contentType)
                        putS3Object(tag, sha1FileName, sha1File, "text/plain")
                    }
                }
            }
        } finally {
            sha1File.delete()
        }
    }

    private suspend fun uploadFileToGitHub(
        client: HttpClient,
        repository: String,
        releaseId: String,
        token: String,
        file: File,
        fileName: String,
        contentType: ContentType,
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
            true
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 422) {
                logger.lifecycle("Asset already exists: $fileName")
                false
            } else {
                throw e
            }
        }
    }

    private fun putS3Object(
        tag: String,
        name: String,
        file: File,
        contentType: String,
    ) {
        val accessKeyId = requireConfigured(awsAccessKeyId, "AWS_ACCESS_KEY_ID")
        val secretAccessKey = requireConfigured(awsSecretAccessKey, "AWS_SECRET_ACCESS_KEY")
        val baseUrl = requireConfigured(awsBaseUrl, "AWS_BASEURL")
        val region = requireConfigured(awsRegion, "AWS_REGION")
        val bucket = requireConfigured(awsBucket, "AWS_BUCKET")

        S3Client.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey),
                ),
            )
            .endpointOverride(URI.create(baseUrl))
            .region(Region.of(region))
            .build()
            .use { client ->
                client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key("$tag/$name")
                        .contentType(contentType)
                        .build(),
                    RequestBody.fromFile(file),
                )
            }
    }

    private fun requireConfigured(property: Property<String>, name: String): String =
        property.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException(
                "Required property '$name' is missing. Configure it as a task input from the build script.",
            )

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
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}

@DisableCachingByDefault(because = "Uploads release artifacts to external services")
abstract class UploadReleaseAssetTask : ReleaseUploadTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val artifactFile: RegularFileProperty

    @get:Input
    abstract val assetName: Property<String>

    @get:Input
    abstract val assetContentType: Property<String>

    @TaskAction
    fun upload() {
        uploadReleaseAsset(
            name = assetName.get(),
            contentType = assetContentType.get(),
            file = artifactFile.get().asFile,
        )
    }
}

@DisableCachingByDefault(because = "Uploads release artifacts to external services")
abstract class UploadAndroidApksTask : ReleaseUploadTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val apkDirectory: DirectoryProperty

    @TaskAction
    fun uploadApks() {
        val fullVersion = releaseFullVersion.get()
        val apkFiles = apkDirectory.asFileTree.files
            .filter { it.isFile && it.extension == "apk" && it.name.contains("release") }
            .sortedBy { it.name }

        if (apkFiles.isEmpty()) {
            throw GradleException("No release APKs found in ${apkDirectory.get().asFile.absolutePath}")
        }

        apkFiles.forEach { file ->
            val arch = file.name
                .removePrefix("android-default-")
                .removeSuffix("-release.apk")
                .takeIf { it != file.name }
                ?: throw GradleException("Cannot infer Android architecture from file name '${file.name}'")

            uploadReleaseAsset(
                name = ReleaseArtifactNames.androidApp(fullVersion, arch),
                contentType = "application/vnd.android.package-archive",
                file = file,
            )
        }
    }
}

@DisableCachingByDefault(because = "Uploads release artifacts to external services")
abstract class UploadDesktopInstallersTask : ReleaseUploadTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val zipDistribution: RegularFileProperty

    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaryDirectory: DirectoryProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val linuxAppImage: RegularFileProperty

    @TaskAction
    fun uploadInstallers() {
        val fullVersion = releaseFullVersion.get()

        when (currentReleaseHostOs()) {
            ReleaseHostOs.WINDOWS -> uploadReleaseAsset(
                name = ReleaseArtifactNames.desktopDistributionFile(
                    fullVersion = fullVersion,
                    osName = "windows",
                    extension = "zip",
                ),
                contentType = "application/x-zip",
                file = requiredFile(zipDistribution, "zipDistribution"),
            )

            ReleaseHostOs.MACOS -> {
                if (currentReleaseHostArch() == "x86_64") {
                    uploadReleaseAsset(
                        name = ReleaseArtifactNames.desktopDistributionFile(
                            fullVersion = fullVersion,
                            osName = "macos",
                            extension = "zip",
                        ),
                        contentType = "application/x-zip",
                        file = requiredFile(zipDistribution, "zipDistribution"),
                    )
                } else {
                    uploadReleaseAsset(
                        name = ReleaseArtifactNames.desktopDistributionFile(
                            fullVersion = fullVersion,
                            osName = "macos",
                            extension = "dmg",
                        ),
                        contentType = "application/octet-stream",
                        file = findSingleFile(
                            directory = requiredDirectory(binaryDirectory, "binaryDirectory").resolve("dmg"),
                            extension = "dmg",
                        ),
                    )
                }
            }

            ReleaseHostOs.LINUX -> uploadReleaseAsset(
                name = ReleaseArtifactNames.desktopDistributionFile(
                    fullVersion = fullVersion,
                    osName = "linux",
                    archName = "x86_64",
                    extension = "appimage",
                ),
                contentType = "application/x-appimage",
                file = requiredFile(linuxAppImage, "linuxAppImage"),
            )
        }
    }

    private fun requiredFile(property: RegularFileProperty, propertyName: String): File =
        property.orNull?.asFile
            ?: throw GradleException("Required property '$propertyName' is not configured.")

    private fun requiredDirectory(property: DirectoryProperty, propertyName: String): File =
        property.orNull?.asFile
            ?: throw GradleException("Required property '$propertyName' is not configured.")

    private fun findSingleFile(directory: File, extension: String): File {
        if (!directory.isDirectory) {
            throw GradleException("Directory '${directory.absolutePath}' does not exist.")
        }
        return directory.walk()
            .singleOrNull { it.isFile && it.extension.equals(extension, ignoreCase = true) }
            ?: throw GradleException("Expected exactly one '.$extension' file in ${directory.absolutePath}")
    }
}

@DisableCachingByDefault(because = "Writes App Store Connect API key material for external tooling")
abstract class PrepareAppStoreConnectApiKeyTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val apiKeyId: Property<String>

    @get:Input
    @get:Optional
    abstract val apiPrivateKey: Property<String>

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputKeyFile: RegularFileProperty

    @TaskAction
    fun writeKey() {
        val keyId = apiKeyId.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPSTORE_API_KEY_ID is not provided, cannot prepare the key.")
        val privateKey = apiPrivateKey.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPSTORE_API_PRIVATE_KEY is not provided, cannot prepare the key.")

        val targetFile = outputKeyFile.get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeText(privateKey)
        logger.lifecycle("Prepared App Store Connect key: AuthKey_${keyId}.p8")
    }
}

@DisableCachingByDefault(because = "Uploads an IPA through iTMSTransporter")
abstract class UploadAppStoreConnectTestflightTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val ipaFile: RegularFileProperty

    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val apiKeyId: Property<String>

    @get:Input
    @get:Optional
    abstract val apiIssuerId: Property<String>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apiPrivateKeyFile: RegularFileProperty

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @TaskAction
    fun upload() {
        val resolvedApiKeyId = apiKeyId.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPSTORE_API_KEY_ID is not provided.")
        val resolvedApiIssuerId = apiIssuerId.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPSTORE_ISSUER_ID is not provided.")

        try {
            execOperations.exec {
                workingDir = workingDirectory.get().asFile
                commandLine(
                    "xcrun",
                    "iTMSTransporter",
                    "-m",
                    "upload",
                    "-assetFile",
                    ipaFile.get().asFile.absolutePath,
                    "-apiKey",
                    resolvedApiKeyId,
                    "-apiIssuer",
                    resolvedApiIssuerId,
                    "-app_platform",
                    "ios",
                    "-v",
                    "eXtreme",
                )
            }
        } finally {
            apiPrivateKeyFile.orNull?.asFile?.delete()
        }
    }
}
