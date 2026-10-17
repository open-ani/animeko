/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.tools.update.DeltaPackageAssembler
import me.him188.ani.app.tools.update.DeltaPackageProgress
import me.him188.ani.app.tools.update.InstallationFailureReason
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(TestOnly::class)
class DevBuildsStateTest {
    private val macosSpec = DevBuildPackageSpec(listOf("ani-macos-aarch64.dmg"), DevBuildPackageKind.MACOS_DMG)
    private val windowsSpec = DevBuildPackageSpec(listOf("ani-windows-x64.zip"), DevBuildPackageKind.DESKTOP_ZIP)
    private val linuxSpec = DevBuildPackageSpec(listOf("ani-linux-x64.AppImage"), DevBuildPackageKind.LINUX_APPIMAGE)
    private val androidSpec = DevBuildPackageSpec(
        listOf("ani-android-arm64-v8a-release.apk", "ani-android-universal-release.apk"),
        DevBuildPackageKind.ANDROID_APK,
        debugArtifactNames = listOf("ani-android-arm64-v8a-debug.apk", "ani-android-universal-debug.apk"),
    )

    private fun TestScope.createState(
        client: HttpClient,
        spec: DevBuildPackageSpec,
        installer: FakeInstaller,
        saveDir: SystemPath,
        token: String = "token",
        currentVersionName: String = "4.12.0-main-aaaaaaaa",
        deltaAssembler: DeltaPackageAssembler? = null,
    ) = DevBuildsState(
        api = GitHubDevBuildApi(client),
        spec = spec,
        installer = installer,
        deltaAssembler = deltaAssembler,
        saveDir = saveDir,
        getToken = { token },
        currentVersionName = currentVersionName,
        backgroundScope = backgroundScope,
        installDispatcher = Dispatchers.Unconfined,
    )

    private val macosDeltaSpec = macosSpec.copy(deltaPackageName = "ani-macos-aarch64.zip")

    private fun deltaAssembler(
        block: suspend (
            packageName: String,
            manifestUrl: String,
            packageUrl: suspend () -> String,
            saveDir: SystemPath,
            onProgress: (DeltaPackageProgress) -> Unit,
        ) -> SystemPath,
    ) = object : DeltaPackageAssembler {
        override suspend fun assemble(
            packageName: String,
            manifestUrl: String,
            packageUrl: suspend () -> String,
            saveDir: SystemPath,
            onProgress: (DeltaPackageProgress) -> Unit,
        ): SystemPath = block(packageName, manifestUrl, packageUrl, saveDir, onProgress)
    }

    /**
     * 在 [fullGitHubMockHandler] 的基础上, 运行 200 还上传了 [macosDeltaSpec] 的 zip 发布包 (artifact 20) 和清单 (artifact 21).
     * 两者的下载接口重定向到各自的直链, 发布包的直链每次取得时签名不同. 运行里还有一个已过期的同名发布包.
     */
    private fun deltaGitHubMockClient(dmg: ByteArray, requests: MutableList<HttpRequestData>): HttpClient {
        val full = fullGitHubMockHandler("ani-macos-aarch64.dmg", dmg)
        var resolved = 0
        return gitHubMockClient { request ->
            requests += request
            val path = request.url.encodedPath
            fun redirect(location: String) = if (request.headers[HttpHeaders.Authorization] == "Bearer token") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, location))
            } else {
                respondJson("""{"message": "Bad credentials"}""", HttpStatusCode.Unauthorized)
            }
            when {
                path.endsWith("/actions/runs/200/artifacts") -> respondJson(
                    """{"artifacts": [
                      {"id": 10, "name": "ani-macos-aarch64.dmg", "size_in_bytes": 1234, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/10/zip", "expired": false,
                       "workflow_run": {"id": 200, "head_sha": "$SHA_A"}},
                      {"id": 22, "name": "ani-macos-aarch64.zip", "size_in_bytes": 300, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/22/zip", "expired": true,
                       "workflow_run": {"id": 200, "head_sha": "$SHA_A"}},
                      {"id": 20, "name": "ani-macos-aarch64.zip", "size_in_bytes": 300, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/20/zip", "expired": false,
                       "workflow_run": {"id": 200, "head_sha": "$SHA_A"}},
                      {"id": 21, "name": "ani-macos-aarch64.zip.manifest.json", "size_in_bytes": 30, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/21/zip", "expired": false,
                       "workflow_run": {"id": 200, "head_sha": "$SHA_A"}}
                    ]}""",
                )

                path.endsWith("/artifacts/20/zip") -> redirect("https://blob.example.com/package.zip?sig=${++resolved}")
                path.endsWith("/artifacts/21/zip") -> redirect("https://blob.example.com/package.zip.manifest.json?sig=1")
                path.endsWith("/artifacts/22/zip") -> error("expired artifact must not be used")
                else -> full(request)
            }
        }
    }

    private inline fun withTempDir(block: (SystemPath) -> Unit) {
        val dir = SystemPaths.createTempDirectory("dev-builds-state-test")
        try {
            block(dir.resolve("dev-builds"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private suspend fun DevBuildsState.loadCommits(): List<DevBuildCommit> {
        refresh()
        joinTasks()
        return assertIs<DevBuildListState.Loaded>(listState.value).commits
    }

    private suspend fun DevBuildsState.lookupAndJoin(text: String): DevBuildLookupState {
        lookup(text)
        joinTasks()
        return lookupState.value
    }

    private suspend fun DevBuildsState.lookupCommit(text: String): DevBuildLookupResult.Commit =
        assertIs<DevBuildLookupResult.Commit>(assertIs<DevBuildLookupState.Resolved>(lookupAndJoin(text)).result)

    private suspend fun DevBuildsState.lookupFailure(text: String): DevBuildLookupFailure =
        assertIs<DevBuildLookupState.Failed>(lookupAndJoin(text)).failure

    @Test
    fun `refresh merges commits, build status and the platform artifact`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf())
            val state = createState(client, macosSpec, FakeInstaller(), saveDir)
            assertEquals(DevBuildListState.Idle, state.listState.value)

            val commits = state.loadCommits()
            assertEquals(listOf(SHA_A, SHA_B), commits.map { it.sha })
            assertEquals(DevBuildStatus.SUCCESS, commits[0].build?.status)
            assertEquals(10, commits[0].artifact?.id)
            assertEquals(DevBuildStatus.IN_PROGRESS, commits[1].build?.status)
            assertNull(commits[1].artifact)

            assertEquals("aaaaaaaa", state.currentCommitShortSha)
            assertTrue(state.isCurrentCommit(commits[0]))
            assertFalse(state.isCurrentCommit(commits[1]))
            assertFalse(state.isRefreshing.value)
        }
    }

    @Test
    fun `refresh failure is exposed and a later refresh recovers`() = runTest {
        withTempDir { saveDir ->
            var fail = true
            val healthy = fullGitHubMockHandler("ani-macos-aarch64.dmg", byteArrayOf())
            val client = gitHubMockClient { request ->
                if (fail) {
                    respond(
                        """{"message": "rate limited"}""",
                        HttpStatusCode.Forbidden,
                        headersOf("x-ratelimit-remaining", "0"),
                    )
                } else {
                    healthy(request)
                }
            }
            val state = createState(client, macosSpec, FakeInstaller(), saveDir)
            state.refresh()
            state.joinTasks()
            val failed = assertIs<DevBuildListState.Failed>(state.listState.value)
            val e = assertIs<GitHubApiException>(failed.throwable)
            assertTrue(e.isRateLimited)

            fail = false
            assertEquals(2, state.loadCommits().size)
        }
    }

    @Test
    fun `install requires a github token`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf())
            val state = createState(client, macosSpec, installer, saveDir, token = " ")
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallFailure.TokenRequired, failed.failure)
            assertNull(failed.file)
            assertTrue(installer.installed.isEmpty())

            state.dismissInstallResult()
            assertEquals(DevBuildInstallState.Idle, state.installState.value)
        }
    }

    @Test
    fun `install ignores commits without a package`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val state = createState(fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf()), macosSpec, installer, saveDir)
            val commit = state.loadCommits()[1]
            state.install(commit, testContext)
            state.joinTasks()
            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            assertTrue(installer.installed.isEmpty())
        }
    }

    @Test
    fun `install downloads the package artifact and calls the installer`() = runTest {
        withTempDir { saveDir ->
            val dmg = ByteArray(2048) { (it % 7).toByte() }
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", dmg)
            val state = createState(client, macosSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("ani-main-aaaaaaaa.dmg"), installed)
            assertContentEquals(dmg, installed.readBytes())
            assertEquals(listOf("ani-main-aaaaaaaa.dmg"), saveDir.list().map { it.name })
        }
    }

    @Test
    fun `install assembles the package incrementally from the delta artifacts of the build run`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val requests = mutableListOf<HttpRequestData>()
            val client = deltaGitHubMockClient(byteArrayOf(1), requests)
            val observed = mutableListOf<DevBuildInstallState>()
            lateinit var state: DevBuildsState
            val assembler = deltaAssembler { packageName, manifestUrl, packageUrl, dir, onProgress ->
                observed += state.installState.value
                assertEquals("https://blob.example.com/package.zip.manifest.json?sig=1", manifestUrl)
                assertEquals("https://blob.example.com/package.zip?sig=1", packageUrl())
                onProgress(DeltaPackageProgress(downloadedBytes = 5, totalDownloadBytes = 10, fraction = 0.75f))
                observed += state.installState.value
                dir.resolve(packageName).also { it.toFile().writeBytes(byteArrayOf(9)) }
            }
            state = createState(client, macosDeltaSpec, installer, saveDir, deltaAssembler = assembler)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("ani-main-aaaaaaaa.zip"), installed)
            assertContentEquals(byteArrayOf(9), installed.readBytes())
            val target = DevBuildInstallTarget.of(commit)
            assertEquals(
                listOf<DevBuildInstallState>(
                    DevBuildInstallState.DownloadingIncrementally(target, 0, null, null),
                    DevBuildInstallState.DownloadingIncrementally(target, 5, 10, 0.75f),
                ),
                observed,
            )
            assertTrue(requests.none { it.url.encodedPath.endsWith("/artifacts/10/zip") }, "full artifact must not be downloaded")
        }
    }

    @Test
    fun `failed incremental assembly falls back to the full artifact`() = runTest {
        withTempDir { saveDir ->
            val dmg = ByteArray(1024) { (it % 3).toByte() }
            val installer = FakeInstaller()
            val requests = mutableListOf<HttpRequestData>()
            val assembler = deltaAssembler { _, _, packageUrl, _, _ ->
                packageUrl()
                throw IOException("Range not supported")
            }
            val state = createState(
                deltaGitHubMockClient(dmg, requests), macosDeltaSpec, installer, saveDir,
                deltaAssembler = assembler,
            )
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("ani-main-aaaaaaaa.dmg"), installed)
            assertContentEquals(dmg, installed.readBytes())
            assertTrue(requests.any { it.url.encodedPath.endsWith("/artifacts/10/zip") })
        }
    }

    @Test
    fun `install downloads the full artifact when the run has no delta package`() = runTest {
        withTempDir { saveDir ->
            val dmg = ByteArray(16) { it.toByte() }
            val installer = FakeInstaller()
            val assembler = deltaAssembler { _, _, _, _, _ -> error("no delta package to assemble from") }
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", dmg)
            val state = createState(client, macosDeltaSpec, installer, saveDir, deltaAssembler = assembler)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            assertContentEquals(dmg, installer.installed.single().readBytes())
        }
    }

    @Test
    fun `windows zip package is handed to the installer`() = runTest {
        withTempDir { saveDir ->
            val archive = zipBytes("Ani/Ani.exe" to byteArrayOf(1, 2), "Ani/app/x.jar" to byteArrayOf(3))
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("ani-windows-x64.zip", archive)
            val state = createState(client, windowsSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("ani-main-aaaaaaaa.zip"), installed)
            assertContentEquals(archive, installed.readBytes())
        }
    }

    @Test
    fun `linux appimage waits for manual installation`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("ani-linux-x64.AppImage", byteArrayOf(7))
            val state = createState(client, linuxSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val ready = assertIs<DevBuildInstallState.ReadyForManualInstall>(state.installState.value)
            assertEquals(saveDir.resolve("ani-main-aaaaaaaa.AppImage"), ready.file)
            assertContentEquals(byteArrayOf(7), ready.file.readBytes())
            assertTrue(ready.file.toFile().canExecute())
            assertTrue(installer.installed.isEmpty())

            state.revealPackage(ready.file, testContext)
            assertEquals(listOf(ready.file), installer.revealed)
            state.dismissInstallResult()
            assertEquals(DevBuildInstallState.Idle, state.installState.value)
        }
    }

    @Test
    fun `installer failure keeps the package for manual installation`() = runTest {
        withTempDir { saveDir ->
            val result = InstallationResult.Failed(InstallationFailureReason.FAILED_TO_MOUNT_DMG, "boom")
            val installer = FakeInstaller(result)
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf(1))
            val state = createState(client, macosSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallFailure.Installer(result), failed.failure)
            assertEquals(saveDir.resolve("ani-main-aaaaaaaa.dmg"), failed.file)
        }
    }

    @Test
    fun `network failure during download is reported without a file`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf(), expectedToken = "other")
            val state = createState(client, macosSpec, installer, saveDir)
            val commit = state.loadCommits()[0]

            state.install(commit, testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            val error = assertIs<DevBuildInstallFailure.Error>(failed.failure)
            assertTrue(assertIs<GitHubApiException>(error.throwable).isUnauthorized)
            assertNull(failed.file)
        }
    }

    @Test
    fun `lookup resolves a commit link to its latest build and artifact`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf())
            val state = createState(client, macosSpec, FakeInstaller(), saveDir)
            assertEquals(DevBuildLookupState.Idle, state.lookupState.value)

            val result = state.lookupCommit("https://github.com/open-ani/animeko/commit/$SHA_A")
            assertNull(result.pullRequest)
            assertEquals(SHA_A, result.commit.sha)
            assertEquals("feat(update): first line", result.commit.title)
            assertEquals(200, result.commit.build?.id)
            assertEquals(DevBuildStatus.SUCCESS, result.commit.build?.status)
            assertEquals(10, result.commit.artifact?.id)

            // 短 sha 也可以; 没有安装包的 commit 仍然显示构建状态
            val second = state.lookupCommit(SHA_B.take(8))
            assertEquals(SHA_B, second.commit.sha)
            assertEquals(DevBuildStatus.IN_PROGRESS, second.commit.build?.status)
            assertNull(second.commit.artifact)

            state.clearLookup()
            assertEquals(DevBuildLookupState.Idle, state.lookupState.value)
        }
    }

    @Test
    fun `lookup resolves a pull request to its head commit`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf())
            val state = createState(client, macosSpec, FakeInstaller(), saveDir)

            val result = state.lookupCommit("https://github.com/open-ani/animeko/pull/42/files")
            val pr = assertNotNull(result.pullRequest)
            assertEquals(42, pr.number)
            assertEquals("feat: pr title", pr.title)
            assertEquals("feat/x", pr.headRef)
            assertFalse(pr.isFromFork)
            assertEquals(SHA_A, result.commit.sha)
            assertEquals(10, result.commit.artifact?.id)

            val notFound = assertIs<DevBuildLookupFailure.Error>(state.lookupFailure("#43"))
            assertTrue(assertIs<GitHubApiException>(notFound.throwable).isNotFound)
        }
    }

    @Test
    fun `fork pull request without a release apk falls back to the debug apk`() = runTest {
        withTempDir { saveDir ->
            val forkSha = "cccccccc33333333cccccccc33333333cccccccc"
            val client = gitHubMockClient { request ->
                val path = request.url.encodedPath
                when {
                    path.endsWith("/pulls/7") -> respondJson(pullRequestJson(7, forkSha, headRepo = "someone/animeko"))
                    path.endsWith("/commits/$forkSha") -> respondJson(
                        """{"sha": "$forkSha", "html_url": "", "commit": {"message": "fork change"}}""",
                    )

                    path.endsWith("/build.yml/runs") -> {
                        assertEquals(forkSha, request.url.parameters["head_sha"])
                        respondJson(
                            """{"workflow_runs": [
                              {"id": 500, "head_sha": "$forkSha", "status": "completed", "conclusion": "success", "html_url": ""}
                            ]}""",
                        )
                    }

                    path.endsWith("/actions/runs/500/artifacts") -> respondJson(
                        """{"artifacts": [
                          {"id": 51, "name": "ani-android-universal-debug.apk", "size_in_bytes": 2, "archive_download_url": "u51", "expired": false,
                           "workflow_run": {"id": 500, "head_sha": "$forkSha"}},
                          {"id": 52, "name": "ani-android-arm64-v8a-debug.apk", "size_in_bytes": 3, "archive_download_url": "u52", "expired": false,
                           "workflow_run": {"id": 500, "head_sha": "$forkSha"}},
                          {"id": 53, "name": "ani-macos-aarch64.dmg", "size_in_bytes": 4, "archive_download_url": "u53", "expired": false,
                           "workflow_run": {"id": 500, "head_sha": "$forkSha"}}
                        ]}""",
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }
            val state = createState(client, androidSpec, FakeInstaller(), saveDir)

            val result = state.lookupCommit("https://github.com/open-ani/animeko/pull/7")
            assertTrue(assertNotNull(result.pullRequest).isFromFork)
            val artifact = assertNotNull(result.commit.artifact)
            assertEquals("ani-android-arm64-v8a-debug.apk", artifact.name)
            assertTrue(state.spec.isDebugArtifact(artifact.name))
        }
    }

    @Test
    fun `lookup resolves workflow run and artifact links`() = runTest {
        withTempDir { saveDir ->
            val client = fullGitHubMockClient("ani-macos-aarch64.dmg", byteArrayOf())
            val state = createState(client, macosSpec, FakeInstaller(), saveDir)

            val run = state.lookupCommit("https://github.com/open-ani/animeko/actions/runs/100/job/1")
            assertEquals(SHA_A, run.commit.sha)
            assertEquals(100, run.commit.build?.id)
            assertEquals(DevBuildStatus.FAILURE, run.commit.build?.status)
            assertNull(run.commit.artifact, "run 100 uploaded nothing")

            val artifact = state.lookupCommit("https://github.com/open-ani/animeko/actions/runs/200/artifacts/10")
            assertEquals(SHA_A, artifact.commit.sha)
            assertEquals(200, artifact.commit.build?.id)
            assertEquals(10, artifact.commit.artifact?.id)

            // 其他平台的 artifact
            val windows = createState(client, windowsSpec, FakeInstaller(), saveDir)
            val mismatch = assertIs<DevBuildLookupFailure.ArtifactNotForPlatform>(
                windows.lookupFailure("https://api.github.com/repos/open-ani/animeko/actions/artifacts/10/zip"),
            )
            assertEquals("ani-macos-aarch64.dmg", mismatch.artifactName)
        }
    }

    @Test
    fun `package links are accepted only with the platform extension`() = runTest {
        withTempDir { saveDir ->
            val client = gitHubMockClient { error("no requests expected: ${it.url}") }
            val state = createState(client, macosSpec, FakeInstaller(), saveDir)

            val url = "https://github.com/open-ani/animeko/releases/download/v4.12.0/ani-4.12.0-macos-aarch64.dmg"
            val resolved = assertIs<DevBuildLookupState.Resolved>(state.lookupAndJoin(url))
            assertEquals(DevBuildLookupResult.Package(url, "ani-4.12.0-macos-aarch64.dmg"), resolved.result)

            val unsupported = assertIs<DevBuildLookupFailure.UnsupportedPackage>(
                state.lookupFailure("https://example.com/ani-windows.zip"),
            )
            assertEquals("ani-windows.zip", unsupported.fileName)
            assertEquals(DevBuildLookupFailure.Unrecognized, state.lookupFailure("what is this"))
            assertEquals(
                DevBuildLookupFailure.Unrecognized,
                state.lookupFailure("https://github.com/other/repo/commit/$SHA_A"),
            )
        }
    }

    @Test
    fun `installPackage downloads the package following redirects without a token`() = runTest {
        withTempDir { saveDir ->
            val dmg = ByteArray(1024) { (it % 5).toByte() }
            val installer = FakeInstaller()
            val requests = mutableListOf<HttpRequestData>()
            val client = gitHubMockClient { request ->
                requests += request
                when (request.url.host) {
                    "github.com" -> respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://objects.example.com/blob?sig=1"),
                    )

                    "objects.example.com" -> respond(
                        dmg,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentLength, dmg.size.toString()),
                    )

                    else -> error("Unexpected request: ${request.url}")
                }
            }
            val state = createState(client, macosSpec, installer, saveDir, token = "")
            val url = "https://github.com/open-ani/animeko/releases/download/v4.12.0/ani-4.12.0-macos-aarch64.dmg"

            state.installPackage(url, "ani-4.12.0-macos-aarch64.dmg", testContext)
            state.joinTasks()

            assertEquals(DevBuildInstallState.Idle, state.installState.value)
            val installed = installer.installed.single()
            assertEquals(saveDir.resolve("ani-4.12.0-macos-aarch64.dmg"), installed)
            assertContentEquals(dmg, installed.readBytes())
            assertEquals(listOf("github.com", "objects.example.com"), requests.map { it.url.host })
            assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == null })
        }
    }

    @Test
    fun `installPackage failure reports the target`() = runTest {
        withTempDir { saveDir ->
            val installer = FakeInstaller()
            val client = gitHubMockClient { respondJson("""{"message": "gone"}""", HttpStatusCode.NotFound) }
            val state = createState(client, macosSpec, installer, saveDir)
            val url = "https://example.com/ani.dmg"

            state.installPackage(url, "ani.dmg", testContext)
            state.joinTasks()

            val failed = assertIs<DevBuildInstallState.Failed>(state.installState.value)
            assertEquals(DevBuildInstallTarget(url, "ani.dmg", url), failed.target)
            assertIs<DevBuildInstallFailure.Error>(failed.failure)
            assertTrue(installer.installed.isEmpty())
        }
    }
}
