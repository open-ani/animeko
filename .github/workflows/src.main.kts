#!/usr/bin/env kotlin

/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 也可以在 IDE 里右键 Run

@file:CompilerOptions("-Xmulti-dollar-interpolation", "-Xdont-warn-on-error-suppression")
@file:Suppress("UNSUPPORTED_FEATURE", "UNSUPPORTED")

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.0.1")
@file:Repository("https://bindings.krzeminski.it")

// Build
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("gmitch215:setup-java:6d2c5e1f82f180ae79f799f0ed6e3e5efb4e664d")
@file:DependsOn("org.jetbrains:annotations:23.0.0")
@file:DependsOn("actions:github-script:v7")
@file:DependsOn("gradle:actions__setup-gradle:v3")
@file:DependsOn("nick-fields:retry:v3")
@file:DependsOn("timheuer:base64-to-file:v1.1")
@file:DependsOn("actions:upload-artifact:v4")
@file:DependsOn("actions:download-artifact:v4")
@file:DependsOn("reactivecircus:android-emulator-runner:v2")
@file:DependsOn("jlumbroso:free-disk-space:v1.3.1")

// Release
@file:DependsOn("dawidd6:action-get-tag:v1")
@file:DependsOn("bhowell2:github-substring-action:v1.0.0")
@file:DependsOn("softprops:action-gh-release:v1")
@file:DependsOn("snow-actions:qrcode:v1.0.0")


import Secrets.AWS_ACCESS_KEY_ID
import Secrets.AWS_BASEURL
import Secrets.AWS_BUCKET
import Secrets.AWS_REGION
import Secrets.AWS_SECRET_ACCESS_KEY
import Secrets.GITHUB_REPOSITORY
import Secrets.SIGNING_RELEASE_KEYALIAS
import Secrets.SIGNING_RELEASE_KEYPASSWORD
import Secrets.SIGNING_RELEASE_STOREFILE
import Secrets.SIGNING_RELEASE_STOREPASSWORD
import Secrets.DANDANPLAY_APP_ID
import Secrets.DANDANPLAY_APP_SECRET
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.DownloadArtifact
import io.github.typesafegithub.workflows.actions.actions.GithubScript
import io.github.typesafegithub.workflows.actions.actions.UploadArtifact
import io.github.typesafegithub.workflows.actions.bhowell2.GithubSubstringAction_Untyped
import io.github.typesafegithub.workflows.actions.dawidd6.ActionGetTag_Untyped
import io.github.typesafegithub.workflows.actions.gmitch215.SetupJava_Untyped
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.actions.jlumbroso.FreeDiskSpace_Untyped
import io.github.typesafegithub.workflows.actions.nickfields.Retry_Untyped
import io.github.typesafegithub.workflows.actions.reactivecircus.AndroidEmulatorRunner
import io.github.typesafegithub.workflows.actions.snowactions.Qrcode_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.actions.timheuer.Base64ToFile_Untyped
import io.github.typesafegithub.workflows.domain.AbstractResult
import io.github.typesafegithub.workflows.domain.ActionStep
import io.github.typesafegithub.workflows.domain.CommandStep
import io.github.typesafegithub.workflows.domain.Job
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.Mode
import io.github.typesafegithub.workflows.domain.Permission
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.Shell
import io.github.typesafegithub.workflows.domain.Step
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.WorkflowBuilder
import io.github.typesafegithub.workflows.dsl.expressions.contexts.GitHubContext
import io.github.typesafegithub.workflows.dsl.expressions.contexts.SecretsContext
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import org.intellij.lang.annotations.Language
import kotlin.math.max

check(KotlinVersion.CURRENT.isAtLeast(2, 0, 0)) {
    "This script requires Kotlin 2.0.0 or later"
}

enum class OS {
    WINDOWS,
    UBUNTU,
    MACOS;

    override fun toString(): String = name.lowercase()
}

enum class Arch {
    X64,
    AARCH64;

    override fun toString(): String = name.lowercase()
}

//enum class AndroidArch(
//    val id: String,
//) {
//    ARM64_V8A("arm64-v8a"),
//    X86_64("x86_64"),
//    ARMEABI_V7A("armeabi-v7a"),
//    UNIVERSAL("universal"),
//    ;
//}

object AndroidArch {
    const val ARM64_V8A = "arm64-v8a"
    const val X86_64 = "x86_64"
    const val ARMEABI_V7A = "armeabi-v7a"
    const val UNIVERSAL = "universal"

    val entriesWithoutUniversal = listOf(ARM64_V8A, X86_64, ARMEABI_V7A)
    val entriesWithUniversal = entriesWithoutUniversal + UNIVERSAL
}

// Build 和 Release 共享这个
// Configuration for a Runner
data class MatrixInstance(
    // 定义属性为 val, 就会生成到 yml 的 `matrix` 里.

    /**
     * 用于 matrix 的 id
     */
    val runner: Runner,
    /**
     * 显示的名字, 不能变更, 否则会导致 PR Rules 失效
     */
    val name: String = runner.name,
    /**
     * GitHub Actions 的规范名称, e.g. `ubuntu-20.04`, `windows-2019`.
     */
    val runsOn: Set<String> = runner.labels,

    /**
     * 只在脚本内部判断 OS 使用, 不影响 github 调度机器
     * @see OS
     */
    val os: OS = runner.os,
    /**
     * 只在脚本内部判断 OS 使用, 不影响 github 调度机器
     * @see Arch
     */
    val arch: Arch = runner.arch,

    /**
     * `false` = GitHub Actions 的免费机器
     */
    val selfHosted: Boolean = runner is Runner.SelfHosted,
    /**
     * 有一台机器是 true 就行
     */
    val uploadApk: Boolean,
    val runAndroidInstrumentedTests: Boolean = uploadApk,
    /**
     * Compose for Desktop 的 resource 标识符, e.g. `windows-x64`
     */
    val composeResourceTriple: String,
    val runTests: Boolean = true,
    /**
     * 每种机器必须至少有一个是 true, 否则 release 时传不全
     */
    val uploadDesktopInstallers: Boolean = true,
    /**
     * 追加到所有 Gradle 命令的参数. 无需 quote
     */
    val extraGradleArgs: List<String> = emptyList(),
    /**
     * Self hosted 机器已经配好了环境, 无需安装
     */
    val installNativeDeps: Boolean = !selfHosted,
    val buildIosFramework: Boolean = false,
    val buildAllAndroidAbis: Boolean = true,

    // Gradle command line args
    private val gradleHeap: String = "4g",
    private val kotlinCompilerHeap: String = "4g",
    /**
     * 只能在内存比较大的时候用.
     */
    private val gradleParallel: Boolean = selfHosted,
) {
    @Suppress("unused")
    val gradleArgs = buildList {

        /**
         * Windows 上必须 quote, Unix 上 quote 或不 quote 都行. 所以我们统一 quote.
         */
        fun quote(s: String): String {
            if (s.startsWith("\"")) {
                return s  // already quoted
            }
            return "\"$s\""
        }

        add(quote("--scan"))
        add(quote("-Porg.gradle.daemon.idletimeout=60000"))
        add(quote("-Pkotlin.native.ignoreDisabledTargets=true"))
        add(quote("-Dfile.encoding=UTF-8"))

        if (os == OS.WINDOWS) {
            add(quote("-DCMAKE_TOOLCHAIN_FILE=C:/vcpkg/scripts/buildsystems/vcpkg.cmake"))
            add(quote("-DBoost_INCLUDE_DIR=C:/vcpkg/installed/x64-windows/include"))
        }

        add(quote("-Dorg.gradle.jvmargs=-Xmx${gradleHeap}"))
        add(quote("-Dkotlin.daemon.jvm.options=-Xmx${kotlinCompilerHeap}"))
        add(quote("-Pani.dandanplay.app.id=${expr { secrets.DANDANPLAY_APP_ID }}"))
        add(quote("-Pani.dandanplay.app.secret=${expr { secrets.DANDANPLAY_APP_SECRET }}"))

        if (gradleParallel) {
            add(quote("--parallel"))
        }

        extraGradleArgs.forEach {
            add(quote(it))
        }
    }.joinToString(" ")

    init {
        if (buildAllAndroidAbis) {
            require(!gradleArgs.contains(ANI_ANDROID_ABIS)) { "You must not set `-P${ANI_ANDROID_ABIS}` when you want to build all Android ABIs" }
        } else {
            require(gradleArgs.contains(ANI_ANDROID_ABIS)) { "You must set `-P${ANI_ANDROID_ABIS}` when you don't want to build all Android ABIs" }
        }
    }
}

@Suppress("PropertyName")
val ANI_ANDROID_ABIS = "ani.android.abis"

sealed class Runner(
    val id: String,
    val name: String,
    val os: OS,
    val arch: Arch,
    // GitHub Actions labels, e.g. `windows-2019`, `macos-13`, `self-hosted`, `Windows`, `X64`
    val labels: Set<String>,
) {
    // Intermediate sealed classes
    sealed class GithubHosted(
        id: String,
        displayName: String,
        os: OS,
        arch: Arch,
        labels: Set<String>
    ) : Runner(id, displayName, os, arch, labels)

    sealed class SelfHosted(
        id: String,
        displayName: String,
        os: OS,
        arch: Arch,
        labels: Set<String>
    ) : Runner(id, displayName, os, arch, labels)

    // Objects under GithubHosted
    object GithubWindowsServer2019 : GithubHosted(
        id = "github-windows-2019",
        displayName = "Windows Server 2019 x86_64 (GitHub)",
        os = OS.WINDOWS,
        arch = Arch.X64,
        labels = setOf("windows-2019"),
    )

    object GithubWindowsServer2022 : GithubHosted(
        id = "github-windows-2022",
        displayName = "Windows Server 2022 x86_64 (GitHub)",
        os = OS.WINDOWS,
        arch = Arch.X64,
        labels = setOf("windows-2022"),
    )

    object GithubMacOS13 : GithubHosted(
        id = "github-macos-13",
        displayName = "macOS 13 x86_64 (GitHub)",
        os = OS.MACOS,
        arch = Arch.X64,
        labels = setOf("macos-13"),
    )

    object GithubMacOS14 : GithubHosted(
        id = "github-macos-14",
        displayName = "macOS 14 AArch64 (GitHub)",
        os = OS.MACOS,
        arch = Arch.AARCH64,
        labels = setOf("macos-14"),
    )

    object GithubMacOS15 : GithubHosted(
        id = "github-macos-15",
        displayName = "macOS 15 AArch64 (GitHub)",
        os = OS.MACOS,
        arch = Arch.AARCH64,
        labels = setOf("macos-15"),
    )

    object GithubUbuntu2404 : GithubHosted(
        id = "github-ubuntu-2404",
        displayName = "Ubuntu 24.04 x86_64 (GitHub)",
        os = OS.UBUNTU,
        arch = Arch.X64,
        labels = setOf("ubuntu-24.04"),
    )

    // Objects under SelfHosted
    object SelfHostedWindows10 : SelfHosted(
        id = "self-hosted-windows-10",
        displayName = "Windows 10 x86_64 (Self-Hosted)",
        os = OS.WINDOWS,
        arch = Arch.X64,
        labels = setOf("self-hosted", "Windows", "X64"),
    )

    object SelfHostedMacOS15 : SelfHosted(
        id = "self-hosted-macos-15",
        displayName = "macOS 15 AArch64 (Self-Hosted)",
        os = OS.MACOS,
        arch = Arch.AARCH64,
        labels = setOf("self-hosted", "macOS", "ARM64"),
    )

//    companion object {
//        val entries: List<Runner> = listOf(
//            GithubWindowsServer2019,
//            GithubWindowsServer2022,
//            GithubMacOS13,
//            GithubMacOS14,
//            GithubUbuntu2404,
//            SelfHostedWindows10,
//            SelfHostedMacOS15,
//        )
//    }

    override fun toString(): String = id
}

val Runner.isSelfHosted: Boolean
    get() = this is Runner.SelfHosted

// Machines for Build and Release
lateinit var buildMatrixInstances: List<MatrixInstance>
lateinit var releaseMatrixInstances: List<MatrixInstance>

run {
    val selfWin10 = MatrixInstance(
        runner = Runner.SelfHostedWindows10,
        uploadApk = false,
        composeResourceTriple = "windows-x64",
        uploadDesktopInstallers = false,
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=x86_64",
        ),
        buildAllAndroidAbis = false, // 只有 win server 2019 构建的包才能正常使用 anitorrent
        gradleHeap = "6g",
        kotlinCompilerHeap = "6g",
    )
    val ghWin2019 = MatrixInstance(
        runner = Runner.GithubWindowsServer2019,
        name = "Windows Server 2019 x86_64",
        uploadApk = false,
        composeResourceTriple = "windows-x64",
        uploadDesktopInstallers = true,
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=x86_64",
        ),
        buildAllAndroidAbis = false,
        gradleHeap = "4g",
        gradleParallel = true,
    )
    val ghUbuntu2404 = MatrixInstance(
        runner = Runner.GithubUbuntu2404,
        uploadApk = false,
        runAndroidInstrumentedTests = true,
        composeResourceTriple = "linux-x64",
        runTests = false,
        uploadDesktopInstallers = false,
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=x86_64",
        ),
        buildAllAndroidAbis = false,
        gradleHeap = "6g",
        kotlinCompilerHeap = "6g",
    )
    val ghMac13 = MatrixInstance(
        runner = Runner.GithubMacOS13,
        uploadApk = true, // all ABIs
        runAndroidInstrumentedTests = false,
        composeResourceTriple = "macos-x64",
        uploadDesktopInstallers = true,
        extraGradleArgs = listOf(),
        buildIosFramework = false,
        buildAllAndroidAbis = true,
        gradleHeap = "6g",
        kotlinCompilerHeap = "6g",
    )
    val selfMac15 = MatrixInstance(
        runner = Runner.SelfHostedMacOS15,
        uploadApk = false, // upload arm64-v8a once finished
        runAndroidInstrumentedTests = true,
        composeResourceTriple = "macos-arm64",
        uploadDesktopInstallers = true,
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=arm64-v8a",
        ),
        buildAllAndroidAbis = false,
        gradleHeap = "6g",
        kotlinCompilerHeap = "4g",
        gradleParallel = true,
    )
    
    buildMatrixInstances = listOf(
        selfWin10,
        ghWin2019,
        ghUbuntu2404,
        ghMac13,
        selfMac15,
    )
    
    releaseMatrixInstances = listOf(
        ghWin2019, // win installer
        selfMac15.copy(
            buildAllAndroidAbis = true,
            uploadApk = true,
            extraGradleArgs = selfMac15.extraGradleArgs.filterNot { it.startsWith("-P$ANI_ANDROID_ABIS=") }
        ), // macos installer, android apks
    )
}


class BuildJobOutputs : JobOutputs() {
    var macosAarch64DmgSuccess by output()
    var windowsX64PortableSuccess by output()
}

fun getBuildJobBody(matrix: MatrixInstance): JobBuilder<BuildJobOutputs>.() -> Unit = {
    uses(action = Checkout(submodules_Untyped = "recursive"))

    with(WithMatrix(matrix)) {
        freeSpace()
        deleteLocalProperties()
        installJbr21()
        chmod777()
        setupGradle()

        runGradle(
            name = "Update dev version name",
            tasks = ["updateDevVersionNameFromGit", "\"--no-configuration-cache\""],
        )
        if (matrix.isUbuntu) {
            compileAndAssemble()
            androidConnectedTests()
        } else {
            val prepareSigningKey = prepareSigningKey()
            compileAndAssemble()
            prepareSigningKey?.let {
                buildAndroidApk(it)
            }
            gradleCheck()
            androidConnectedTests()
            val packageOutputs = packageDesktopAndUpload()

            packageOutputs.macosAarch64DmgOutcome?.let {
                jobOutputs.macosAarch64DmgSuccess = it.eq(AbstractResult.Status.Success)
            }

            packageOutputs.windowsX64PortableOutcome?.let {
                jobOutputs.windowsX64PortableSuccess = it.eq(AbstractResult.Status.Success)
            }
        }
        cleanupTempFiles()
    }
}

object ArtifactNames {
    fun windowsPortable() = "ani-windows-portable"
    fun macosDmg(arch: Arch) = "ani-macos-dmg-${arch}"
    fun macosPortable(arch: Arch) = "ani-macos-portable-${arch}"
}

fun getVerifyJobBody(
    buildJobOutputs: BuildJobOutputs,
    runner: Runner,
): JobBuilder<JobOutputs.EMPTY>.() -> Unit = {
    uses(action = Checkout()) // not recursive

    if (!runner.isSelfHosted) {
        // We must not destroy the self-hosted runner, 
        // but we are free to remove anything from the GitHub-hosted runners

        when (runner.os) {
            OS.MACOS -> {
                run(
                    name = "Delete libraries from system",
                    command = shell(
                        $$"""
                        sudo rm -rfv /usr/local/lib/libssl* || true
                        sudo rm -rfv /usr/local/lib/libcrypto* || true
                        sudo rm -rfv /opt/homebrew/lib/libssl* || true
                        sudo rm -rfv /opt/homebrew/lib/libcrypto* || true
                    """.trimIndent(),
                    ),
                    continueOnError = true,
                )
            }

            OS.WINDOWS -> {
                run(
                    name = "Delete libraries from system",
                    shell = Shell.PowerShell,
                    command = shell(
                        $$"""
                        Remove-Item -Path "C:\vcpkg\installed\x64-windows\lib\libssl*" -Recurse -Force -Verbose
                        Remove-Item -Path "C:\vcpkg\installed\x64-windows\lib\libcrypto*" -Recurse -Force -Verbose
                    """.trimIndent(),
                    ),
                    continueOnError = true,
                )
            }

            OS.UBUNTU -> {}
        }
    }

    class VerifyTask(
        val name: String,
        val step: String,
        val timeoutMinutes: Int = 5,
        val `if`: String? = null,
    )

    val tasksToExecute = listOf(
        VerifyTask(
            name = "anitorrent-load-test",
            step = "Check that Anitorrent can be loaded",
        ),
        VerifyTask(
            name = "dandanplay-app-id",
            step = "Check that Dandanplay APP ID is valid",
            `if` = expr { github.isAnimekoRepository and !github.isPullRequest }
        ),
    )

    when (runner.os to runner.arch) {
        OS.WINDOWS to Arch.X64 -> {
            uses(
                name = "Download Windows x64 Portable",
                action = DownloadArtifact(
                    name = ArtifactNames.windowsPortable(),
                    path = "${expr { github.workspace }}/ci-helper/verify",
                ),
            )
            tasksToExecute.forEach { task ->
                run(
                    name = task.step,
                    shell = Shell.PowerShell,
                    command = shell(
                        $$"""
                        powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$${expr { github.workspace }}/ci-helper/verify/run-ani-test-windows-x64.ps1" "$${expr { github.workspace }}\ci-helper\verify" "$${task.name}"
                        """.trimIndent(),
                    ),
                    `if` = task.`if`,
                    timeoutMinutes = task.timeoutMinutes,
                )
            }
        }

        OS.MACOS to Arch.AARCH64 -> {
            uses(
                name = "Download DMG",
                action = DownloadArtifact(name = ArtifactNames.macosDmg(Arch.AARCH64)),
            )
            tasksToExecute.forEach { task ->
                run(
                    name = task.step,
                    command = shell($$""""$GITHUB_WORKSPACE/ci-helper/verify/run-ani-test-macos-aarch64.sh" "$GITHUB_WORKSPACE"/*.dmg $${task.name}"""),
                    `if` = task.`if`,
                    timeoutMinutes = task.timeoutMinutes,
                )
            }
        }

        else -> error("Unsupported OS and arch combination: ${runner.os} ${runner.arch}")
    }
}

fun WorkflowBuilder.addVerifyJob(build: Job<BuildJobOutputs>, runner: Runner, ifExpr: String) {
    job(
        id = "verify_${runner.id}",
        name = """Verify (${runner.name})""",
        needs = listOf(build),
        `if` = if (runner.isSelfHosted) {
            expr { github.isAnimekoRepository and ifExpr }
        } else {
            expr { ifExpr }
        },
        permissions = mapOf(
            Permission.Actions to Mode.Read, // Download artifacts
        ),
        runsOn = RunnerType.Labelled(runner.labels),
        block = getVerifyJobBody(build.outputs, runner),
    )
}

fun WorkflowBuilder.addConsistencyCheckJob(filename: String) {
    job(
        id = "consistency-check",
        name = "Workflow YAML Consistency Check",
        runsOn = RunnerType.UbuntuLatest,
        permissions = mapOf(),
    ) {
        uses(action = Checkout())
        run(
            command = "pip3 install PyYAML",
        )
        val originalPath = """.github/workflows/$filename"""
        val backupPath = """.github/workflows/$filename-check.yml"""
        run(
            command = """cp "$originalPath" "$backupPath" """,
        )
        run(
            command = ".github/workflows/${__FILE__.name}",
        )
        run(command = "python .github/workflows/check_yaml_equivalence.py $originalPath $backupPath")
    }
}

workflow(
    name = "Build",
    on = listOf(
        // Including: 
        // - pushing directly to main
        // - pushing to a branch that has an associated PR
        Push(pathsIgnore = listOf("**/*.md")),
        PullRequest(pathsIgnore = listOf("**/*.md")),
    ),
    sourceFile = __FILE__,
    targetFileName = "build.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
) {
    addConsistencyCheckJob("build.yml")
    // Expands job matrix at compile-time so that we set job-level `if` condition. 
    val builds: List<Pair<MatrixInstance, Job<BuildJobOutputs>>> = buildMatrixInstances.map { matrix ->
        matrix to job(
            id = "build_${matrix.runner.id}",
            name = """Build (${matrix.name})""",
            runsOn = RunnerType.Labelled(matrix.runsOn),
            permissions = mapOf(
                Permission.Actions to Mode.Write, // Upload artifacts
            ),
            `if` = if (matrix.selfHosted) {
                // For self-hosted runners, only run if it's our main repository (not a fork).
                // For security concerns, all external contributors will need approval to run the workflow.
                expr { github.isAnimekoRepository }
            } else {
                null // always
            },
            outputs = BuildJobOutputs(),
            block = getBuildJobBody(matrix),
        )
    }

    builds.filter { (matrix, _) ->
        matrix.runner.os == OS.WINDOWS && matrix.uploadDesktopInstallers
    }.forEach { (_, build) ->
        listOf(
            Runner.GithubWindowsServer2019,
            Runner.GithubWindowsServer2022,
            Runner.SelfHostedWindows10,
        ).forEach { runner ->
            addVerifyJob(build, runner, build.outputs.windowsX64PortableSuccess)
        }
    }

    builds.filter { (matrix, _) ->
        matrix.runner.os == OS.MACOS && matrix.runner.arch == Arch.AARCH64
                && matrix.uploadDesktopInstallers
    }.forEach { (_, build) ->
        listOf(
            Runner.SelfHostedMacOS15,
            Runner.GithubMacOS14,
            Runner.GithubMacOS15,
        ).forEach { runner ->
            addVerifyJob(build, runner, build.outputs.macosAarch64DmgSuccess)
        }
    }
}

operator fun List<Pair<MatrixInstance, Job<BuildJobOutputs>>>.get(runner: Runner): Job<BuildJobOutputs> {
    return first { it.first.runner == runner }.second
}

workflow(
    name = "Release",
    permissions = mapOf(
        Permission.Actions to Mode.Write,
        Permission.Contents to Mode.Write, // Releases
    ),
    on = listOf(
        // Only commiter with write-access can trigger this
        Push(tags = listOf("v*")),
    ),
    sourceFile = __FILE__,
    targetFileName = "release.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
) {
    addConsistencyCheckJob("release.yml")
    val createRelease = job(
        id = "create-release",
        name = "Create Release",
        runsOn = RunnerType.UbuntuLatest,
        outputs = object : JobOutputs() {
            var uploadUrl by output()
            var id by output()
        },
    ) {
        uses(action = Checkout()) // No need to be recursive

        val gitTag = getGitTag()

        val releaseNotes = run(
            name = "Generate Release Notes",
            command = shell(
                $$"""
                  # Specify the file path
                  FILE_PATH="ci-helper/release-template.md"
        
                  # Read the file content
                  file_content=$(cat "$FILE_PATH")
        
                  modified_content="$file_content"
                  # Replace 'string_to_find' with 'string_to_replace_with' in the content
                  modified_content="${modified_content//\$GIT_TAG/$${expr { gitTag.tagExpr }}}"
                  modified_content="${modified_content//\$TAG_VERSION/$${expr { gitTag.tagVersionExpr }}}"
        
                  # Output the result as a step output
                  echo "result<<EOF" >> $GITHUB_OUTPUT
                  echo "$modified_content" >> $GITHUB_OUTPUT
                  echo "EOF" >> $GITHUB_OUTPUT
            """.trimIndent(),
            ),
        )

        val createRelease = uses(
            name = "Create Release",
            action = ActionGhRelease(
                tagName = expr { gitTag.tagExpr },
                name = expr { gitTag.tagVersionExpr },
                body = expr { releaseNotes.outputs["result"] },
                draft = true,
                prerelease_Untyped = expr { contains(gitTag.tagExpr, "'-'") },
            ),
            env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
        )

        jobOutputs.uploadUrl = createRelease.outputs.uploadUrl
        jobOutputs.id = createRelease.outputs.id
    }

    val matrixInstancesForRelease = releaseMatrixInstances

    fun addJob(matrix: MatrixInstance) = with(WithMatrix(matrix)) {
        val jobBody: JobBuilder<JobOutputs.EMPTY>.() -> Unit = {
            uses(action = Checkout(submodules_Untyped = "recursive"))

            val gitTag = getGitTag()

            freeSpace()
            deleteLocalProperties()
            installJbr21()
            chmod777()
            setupGradle()

            runGradle(
                name = "Update Release Version Name",
                tasks = ["updateReleaseVersionNameFromGit", "\"--no-configuration-cache\""],
                env = mapOf(
                    "GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN },
                    "GITHUB_REPOSITORY" to expr { secrets.GITHUB_REPOSITORY },
                    "CI_RELEASE_ID" to expr { createRelease.outputs.id },
                    "CI_TAG" to expr { gitTag.tagExpr },
                ),
            )

            val prepareSigningKey = prepareSigningKey()
            compileAndAssemble()

            prepareSigningKey?.let {
                buildAndroidApk(it)
            }
            // No Check. We've already checked in build

            with(
                CIHelper(
                    releaseIdExpr = createRelease.outputs.id,
                    gitTag,
                ),
            ) {
                uploadAndroidApkToCloud()
                generateQRCodeAndUpload()
                uploadDesktopInstallers()
                uploadComposeLogs()
            }
            cleanupTempFiles()
        }

        job(
            id = "release_${matrix.runner.id}",
            name = matrix.name,
            needs = listOf(createRelease),
            runsOn = RunnerType.Labelled(matrix.runsOn),
            `if` = if (matrix.selfHosted) expr { github.isAnimekoRepository } else null, // Don't run on forks
            block = jobBody,
        )
    }

    for (matrix in matrixInstancesForRelease) {
        addJob(matrix)
    }
}

data class GitTag(
    /**
     * The full git tag, e.g. `v1.0.0`
     */
    val tagExpr: String,
    /**
     * The tag version, e.g. `1.0.0`
     */
    val tagVersionExpr: String,
)

fun JobBuilder<*>.getGitTag(): GitTag {
    val tag = uses(
        name = "Get Tag",
        action = ActionGetTag_Untyped(),
    )

    val tagVersion = uses(
        action = GithubSubstringAction_Untyped(
            value_Untyped = expr { tag.outputs.tag },
            indexOfStr_Untyped = "v",
            defaultReturnValue_Untyped = expr { tag.outputs.tag },
        ),
    )

    return GitTag(
        tagExpr = tag.outputs.tag,
        tagVersionExpr = tagVersion.outputs["substring"],
    )
}

class WithMatrix(
    val matrix: MatrixInstance
) {
    fun JobBuilder<*>.runGradle(
        name: String? = null,
        `if`: String? = null,
        @Language("shell", prefix = "./gradlew ") vararg tasks: String,
        env: Map<String, String> = emptyMap(),
        maxAttempts: Int = 2,
        timeoutMinutes: Int = 120,
    ): ActionStep<Retry_Untyped.Outputs> = uses(
        name = name, 
        `if` = `if`,
        action = Retry_Untyped(
            maxAttempts_Untyped = "$maxAttempts",
            timeoutMinutes_Untyped = "$timeoutMinutes",
            command_Untyped = buildString {
                append("./gradlew ")
                tasks.joinTo(this, " ")
                append(' ')
                append(matrix.gradleArgs)
            },
        ),
        env = env,
    )

    /**
     * GitHub Actions 上给的硬盘比较少, 我们删掉一些不必要的文件来腾出空间.
     */
    fun JobBuilder<*>.freeSpace() {
        if (matrix.isMacOS && !matrix.selfHosted) {
            run(
                name = "Free space for macOS",
                command = shell($$"""chmod +x ./ci-helper/free-space-macos.sh && ./ci-helper/free-space-macos.sh"""),
                continueOnError = true,
            )
        }
        if (matrix.isUbuntu && !matrix.selfHosted) {
            uses(
                name = "Free space for Ubuntu",
                action = FreeDiskSpace_Untyped(
                    // https://github.com/marketplace/actions/free-disk-space-ubuntu
                    toolCache_Untyped = "true",
                    android_Untyped = "false",
                    // others are true
                )
            )
        }
    }

    fun JobBuilder<*>.deleteLocalProperties() {
        run(
            command = shell($$"""rm local.properties"""),
            continueOnError = true,
        )
    }

    fun JobBuilder<*>.installJbr21() {
        // For mac
        fun downloadJbrUnix(
            filename: String,
        ): String {
            val jbrUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$filename"
            val jbrChecksumUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$filename.checksum"

            val jbrFilename = jbrUrl.substringAfterLast('/')

            val jbrLocationExpr = run(
                name = "Resolve JBR location",
                command = shell(
                    $$"""
            # Expand jbrLocationExpr
            jbr_location_expr='$${expr { runner.tool_cache } + "/" + jbrFilename}'
            echo "jbrLocation=$jbr_location_expr" >> $GITHUB_OUTPUT
            """.trimIndent(),
                ),
                shell = Shell.Bash,
//                env = mapOf("MY_PATH" to ),
            ).outputs["jbrLocation"]

            run(
                name = "Get JBR 21 for macOS AArch64",
                command = shell(
                    $$"""
        jbr_location="$jbrLocation"
        checksum_url="$$jbrChecksumUrl"
        checksum_file="checksum.tmp"
        wget -q -O $checksum_file $checksum_url

        expected_checksum=$(awk '{print $1}' $checksum_file)
        file_checksum=""
        
        if [ -f "$jbr_location" ]; then
            file_checksum=$(shasum -a 512 "$jbr_location" | awk '{print $1}')
        fi
        
        if [ "$file_checksum" != "$expected_checksum" ]; then
            wget -q --tries=3 $$jbrUrl -O "$jbr_location"
            file_checksum=$(shasum -a 512 "$jbr_location" | awk '{print $1}')
        fi
        
        if [ "$file_checksum" != "$expected_checksum" ]; then
            echo "Checksum verification failed!" >&2
            rm -f $checksum_file
            exit 1
        fi
        
        rm -f $checksum_file
        file "$jbr_location"
    """.trimIndent(),
                ),
                env = mapOf(
                    "jbrLocation" to expr { jbrLocationExpr },
                ),
                shell = Shell.Bash,
            )

            return jbrLocationExpr
        }

        fun downloadJbrUsingPython(
            filename: String,
        ): String {
            // These URLs should remain the same; only the shell commands change.
            val jbrUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$filename"
            val jbrChecksumUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/$filename.checksum"

            val jbrFilename = jbrUrl.substringAfterLast('/')
            val step = run(
                name = "Get JBR (Windows)",
                command = "python .github/workflows/download_jbr.py",
                // Pass in environment variables that the script can read
                env = mapOf(
                    "RUNNER_TOOL_CACHE" to expr { runner.tool_cache },
                    "JBR_URL" to jbrUrl,
                    "JBR_CHECKSUM_URL" to jbrChecksumUrl,
                ),
                shell = if (matrix.isWindows) Shell.Cmd else Shell.Bash,
            )

            return step.outputs["jbrLocation"]
        }

        when (matrix.runner.os) {
            OS.MACOS -> {
                val jbrLocationExpr = if (matrix.arch == Arch.AARCH64) {
                    downloadJbrUnix("jbrsdk_jcef-21.0.5-osx-aarch64-b631.8.tar.gz")
                } else {
                    downloadJbrUnix("jbrsdk_jcef-21.0.5-osx-x64-b631.8.tar.gz")
                }

                uses(
                    name = "Setup JBR 21 for macOS ",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "jdkfile",
                        javaVersion_Untyped = "21",
                        jdkFile_Untyped = expr { jbrLocationExpr },
                    ),
                    env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
                )
            }

            OS.WINDOWS -> {
                val jbrLocationExpr = downloadJbrUsingPython("jbrsdk_jcef-21.0.5-windows-x64-b750.29.tar.gz")
                uses(
                    name = "Setup JBR 21 for Windows",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "jdkfile",
                        javaVersion_Untyped = "21",
                        jdkFile_Untyped = expr { jbrLocationExpr },
                    ),
                    env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
                )
            }

            OS.UBUNTU -> {
                val jbrLocationExpr = downloadJbrUsingPython("jbrsdk_jcef-21.0.5-linux-x64-b750.29.tar.gz")
                uses(
                    name = "Setup JBR 21 for Ubuntu",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "jdkfile",
                        javaVersion_Untyped = "21",
                        jdkFile_Untyped = expr { jbrLocationExpr },
                    ),
                    env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
                )
            }
        }

        run(
            name = "Dump Local Properties",
            command = shell($$"""echo "jvm.toolchain.version=21" >> local.properties"""),
        )
    }

    fun JobBuilder<*>.installNativeDeps() {
        // Windows
        if (matrix.isWindows and matrix.installNativeDeps) {
            uses(
                name = "Setup vcpkg cache",
                action = GithubScript(
                    script = """
                core.exportVariable('ACTIONS_CACHE_URL', process.env.ACTIONS_CACHE_URL || '');
                core.exportVariable('ACTIONS_RUNTIME_TOKEN', process.env.ACTIONS_RUNTIME_TOKEN || '');
            """.trimIndent(),
                ),
            )
            run(
                name = "Install Native Dependencies for Windows",
                command = "./ci-helper/install-deps-windows.cmd",
                env = mapOf("VCPKG_BINARY_SOURCES" to "clear;x-gha,readwrite"),
            )
        }

        if (matrix.isMacOS and matrix.installNativeDeps) {
            // MacOS
            run(
                name = "Install Native Dependencies for MacOS",
                command = shell($$"""chmod +x ./ci-helper/install-deps-macos-ci.sh && ./ci-helper/install-deps-macos-ci.sh"""),
            )
        }

        run(
            command = shell($$"""cat local.properties"""),
            shell = Shell.Bash,
        )
    }

    fun JobBuilder<*>.chmod777() {
        if (matrix.isUnix) {
            run(
                command = "chmod -R 777 .",
            )
        }
    }

    fun JobBuilder<*>.setupGradle() {
        uses(
            name = "Setup Gradle",
            action = ActionsSetupGradle(
                cacheDisabled = true,
            ),
        )
        uses(
            name = "Clean and download dependencies",
            action = Retry_Untyped(
                maxAttempts_Untyped = "3",
                timeoutMinutes_Untyped = "60",
                command_Untyped = """./gradlew """ + matrix.gradleArgs.replace(
                    "--scan",
                    "--stacktrace",
                ), // com.gradle.develocity.DevelocityException: Internal error in Develocity Gradle plugin: finished notification
            ),
        )
    }

    /**
     * Returns the action step if it's enabled, otherwise returns `null`.
     */
    fun JobBuilder<*>.prepareSigningKey(): ActionStep<Base64ToFile_Untyped.Outputs>? {
        return if (matrix.uploadApk) {
            uses(
                name = "Prepare signing key",
                `if` = expr { github.isAnimekoRepository and !github.isPullRequest },
                action = Base64ToFile_Untyped(
                    fileName_Untyped = "android_signing_key",
                    fileDir_Untyped = "./",
                    encodedString_Untyped = expr { secrets.SIGNING_RELEASE_STOREFILE },
                ),
                continueOnError = true,
            )
        } else {
            null
        }
    }

    fun JobBuilder<*>.compileAndAssemble() {
        runGradle(
            name = "Compile Kotlin",
            tasks = [
                "compileKotlin",
                "compileCommonMainKotlinMetadata",
                "compileJvmMainKotlinMetadata",
                "compileKotlinDesktop",
                "compileKotlinMetadata",
            ],
            maxAttempts = 2,
        )
        // Run separately to avoid OOM
        runGradle(
            name = "Compile Kotlin Android",
            tasks = [
                "compileDebugKotlinAndroid",
                "compileReleaseKotlinAndroid",
            ],
            maxAttempts = 2,
        )
    }

    fun JobBuilder<*>.buildAndroidApk(prepareSigningKey: ActionStep<Base64ToFile_Untyped.Outputs>) {
        if (matrix.uploadApk) {
            runGradle(
                name = "Build Android Debug APKs",
                tasks = [
                    "assembleDebug",
                ],
            )
        }

        for (arch in AndroidArch.entriesWithUniversal) {
            val shouldUpload = if (arch == AndroidArch.UNIVERSAL) {
                matrix.uploadApk and matrix.buildAllAndroidAbis
            } else {
                matrix.uploadApk
            }
            if (shouldUpload) {
                uses(
                    name = "Upload Android Debug APK $arch",
                    action = UploadArtifact(
                        name = "ani-android-${arch}-debug",
                        path_Untyped = "app/android/build/outputs/apk/debug/android-${arch}-debug.apk",
                        overwrite = true,
                    ),
                )
            }
        }

        if (matrix.uploadApk) {
            runGradle(
                name = "Build Android Release APKs",
                `if` = expr { github.isAnimekoRepository and !github.isPullRequest },
                tasks = [
                    "assembleRelease",
                ],
                env = mapOf(
                    "signing_release_storeFileFromRoot" to expr { prepareSigningKey.outputs.filePath },
                    "signing_release_storePassword" to expr { secrets.SIGNING_RELEASE_STOREPASSWORD },
                    "signing_release_keyAlias" to expr { secrets.SIGNING_RELEASE_KEYALIAS },
                    "signing_release_keyPassword" to expr { secrets.SIGNING_RELEASE_KEYPASSWORD },
                ),
            )
        }

        for (arch in AndroidArch.entriesWithUniversal) {
            val shouldUpload = if (arch == AndroidArch.UNIVERSAL) {
                matrix.uploadApk and matrix.buildAllAndroidAbis
            } else {
                matrix.uploadApk
            }
            if (shouldUpload) {
                uses(
                    name = "Upload Android Release APK $arch",
                    action = UploadArtifact(
                        name = "ani-android-${arch}-release",
                        path_Untyped = "app/android/build/outputs/apk/release/android-${arch}-release.apk",
                        overwrite = true,
                    ),
                )
            }
        }
    }

    fun JobBuilder<*>.gradleCheck() {
        if (matrix.runTests) {
            runGradle(
                name = "Check",
                tasks = ["check"],
                maxAttempts = 2,
                timeoutMinutes = 60,
            )
        }
    }

    fun JobBuilder<*>.androidConnectedTests() {
        if (matrix.runAndroidInstrumentedTests && matrix.isUnix) {
            if (matrix.isUbuntu) {
                run(
                    name = "Enable KVM",
                    command = """
                  echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
                  sudo udevadm control --reload-rules
                  sudo udevadm trigger --name-match=kvm
                """.trimIndent(),
                )
            }
            runGradle(
                name = "Build Android Instrumented Tests",
                tasks = [
                    "assembleDebugAndroidTest",
                    "\"-Pandroid.min.sdk=30\"",
                ],
            )
            for (arch in listOfNotNull(
                // test loading anitorrent and other native libraries
                if (matrix.arch == Arch.AARCH64) AndroidEmulatorRunner.Arch.Arm64V8a else null,
                if (matrix.arch == Arch.X64) AndroidEmulatorRunner.Arch.X8664 else null,
            )) {
                // 30 is min for instrumented test (because we have spaces in func names), 
                // 35 is our targetSdk
                for (apiLevel in listOf(30, 35)) {
                    uses(
                        name = "Android Instrumented Test (api=$apiLevel, arch=${arch.stringValue})",
                        action = AndroidEmulatorRunner(
                            apiLevel = apiLevel,
                            arch = arch,
                            script = "./gradlew connectedDebugAndroidTest \"-Pandroid.min.sdk=30\" " + matrix.gradleArgs,
                            emulatorBootTimeout = 1800,
                        ),
                    )
                    if (!matrix.runner.isSelfHosted && matrix.isUnix) {
                        // GitHub hosted runners allow only 14GB space, so we have to remove old emulators before installing new ones
                        run(
                            name = "Uninstall emulators",
                            command = "sdkmanager --uninstall \$(sdkmanager --list | grep emulator | awk '{print \$1}')\n",
                        )
                        run(
                            name = "Remove AVD",
                            command = $$"""
                                echo "Removing Emulator binaries..."
                                rm -rf $ANDROID_HOME/emulator
                                echo "Removing System Images..."
                                rm -rf $ANDROID_HOME/system-images
                            """.trimIndent(),
                        )
                    }
                }
            }
        }
    }

    class PackageDesktopAndUploadOutputs {
        // null means not enabled on this machine
        var macosAarch64DmgOutcome: Step<*>.Outcome? = null
        var windowsX64PortableOutcome: Step<*>.Outcome? = null
    }

    fun JobBuilder<*>.packageDesktopAndUpload(): PackageDesktopAndUploadOutputs {
        if (matrix.isMacOSX64 // not supported
            || !matrix.uploadDesktopInstallers // disabled
        ) {

            return PackageDesktopAndUploadOutputs()
        }

        if (matrix.isWindows) {
            // Windows does not support installers
            runGradle(
                name = "Package Desktop",
                tasks = [
                    "createReleaseDistributable", // portable
                ],
            )
        } else {
            // macOS uses installers
            runGradle(
                name = "Package Desktop",
                tasks = [
                    "packageReleaseDistributionForCurrentOS", // dmg
                ],
            )
        }

        uploadComposeLogs()

        return PackageDesktopAndUploadOutputs().apply {
            if (matrix.isMacOS && matrix.isAArch64) {
                val macosAarch64Dmg = uses(
                    name = "Upload macOS dmg",
                    action = UploadArtifact(
                        name = ArtifactNames.macosDmg(matrix.arch),
                        path_Untyped = "app/desktop/build/compose/binaries/main-release/dmg/Ani-*.dmg",
                        overwrite = true,
                        ifNoFilesFound = UploadArtifact.BehaviorIfNoFilesFound.Error,
                    ),
                )

                this.macosAarch64DmgOutcome = macosAarch64Dmg.outcome
            }

            if (matrix.isMacOS && matrix.isX64) {
                uses(
                    name = "Upload macOS dmg",
                    action = UploadArtifact(
                        name = ArtifactNames.macosPortable(matrix.arch),
                        path_Untyped = "app/desktop/build/compose/binaries/main-release/app/Ani.app",
                        overwrite = true,
                        ifNoFilesFound = UploadArtifact.BehaviorIfNoFilesFound.Error,
                    ),
                )
            }

            if (matrix.isWindows) {
                val windowsX64Portable = uses(
                    name = "Upload Windows packages",
                    action = UploadArtifact(
                        name = ArtifactNames.windowsPortable(),
                        path_Untyped = "app/desktop/build/compose/binaries/main-release/app",
                        overwrite = true,
                        ifNoFilesFound = UploadArtifact.BehaviorIfNoFilesFound.Error,
                    ),
                )

                this.windowsX64PortableOutcome = windowsX64Portable.outcome
            }
        }
    }

    fun JobBuilder<*>.uploadComposeLogs() {
        if (matrix.uploadDesktopInstallers) {
            uses(
                name = "Upload compose logs",
                `if` = expr { always() },
                action = UploadArtifact(
                    name = "compose-logs-${matrix.runner.id}",
                    path_Untyped = "app/desktop/build/compose/logs",
                ),
            )
        }
    }

    fun JobBuilder<*>.cleanupTempFiles() {
        if (matrix.selfHosted and matrix.isMacOSAArch64) {
            run(
                name = "Cleanup temp files",
                command = shell("""chmod +x ./ci-helper/cleanup-temp-files-macos.sh && ./ci-helper/cleanup-temp-files-macos.sh"""),
                continueOnError = true,
            )
        }
    }

    inner class CIHelper(
        releaseIdExpr: String,
        private val gitTag: GitTag,
    ) {
        private val ciHelperSecrets: Map<String, String> = mapOf(
            "GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN },
            "GITHUB_REPOSITORY" to expr { secrets.GITHUB_REPOSITORY },
            "CI_RELEASE_ID" to expr { releaseIdExpr },
            "CI_TAG" to expr { gitTag.tagExpr },
            "UPLOAD_TO_S3" to "true",
            "AWS_ACCESS_KEY_ID" to expr { secrets.AWS_ACCESS_KEY_ID },
            "AWS_SECRET_ACCESS_KEY" to expr { secrets.AWS_SECRET_ACCESS_KEY },
            "AWS_BASEURL" to expr { secrets.AWS_BASEURL },
            "AWS_REGION" to expr { secrets.AWS_REGION },
            "AWS_BUCKET" to expr { secrets.AWS_BUCKET },
        )

        fun JobBuilder<*>.uploadAndroidApkToCloud() {
            if (matrix.uploadApk) {
                runGradle(
                    name = "Upload Android APK for Release",
                    tasks = [":ci-helper:uploadAndroidApk", "\"--no-configuration-cache\""],
                    env = ciHelperSecrets,
                )
            }
        }

        fun JobBuilder<*>.generateQRCodeAndUpload() {
            if (matrix.uploadApk and matrix.buildAllAndroidAbis) {
                uses(
                    name = "Generate QR code for APK (GitHub)",
                    `if` = condition,
                    action = Qrcode_Untyped(
                        text_Untyped = """https://github.com/Him188/ani/releases/download/${expr { gitTag.tagExpr }}/ani-${expr { gitTag.tagVersionExpr }}-universal.apk""",
                        path_Untyped = "apk-qrcode-github.png",
                    ),
                )
                uses(
                    name = "Generate QR code for APK (Cloudflare)",
                    `if` = condition,
                    action = Qrcode_Untyped(
                        text_Untyped = """https://d.myani.org/${expr { gitTag.tagExpr }}/ani-${expr { gitTag.tagVersionExpr }}-universal.apk""",
                        path_Untyped = "apk-qrcode-cloudflare.png",
                    ),
                )
                runGradle(
                    name = "Upload QR code",
                    `if` = condition,
                    tasks = [":ci-helper:uploadAndroidApkQR", "\"--no-configuration-cache\""],
                    env = ciHelperSecrets,
                )
            }
        }

        fun JobBuilder<*>.uploadDesktopInstallers() {
            if (matrix.uploadDesktopInstallers and (!matrix.isMacOSX64)) {
                runGradle(
                    name = "Upload Desktop Installers",
                    tasks = [":ci-helper:uploadDesktopInstallers", "\"--no-configuration-cache\""],
                    env = ciHelperSecrets,
                )
            }
        }
    }
}

/// ENV

object Secrets {
    val SecretsContext.GITHUB_REPOSITORY by SecretsContext.propertyToExprPath
    val SecretsContext.SIGNING_RELEASE_STOREFILE by SecretsContext.propertyToExprPath
    val SecretsContext.SIGNING_RELEASE_STOREPASSWORD by SecretsContext.propertyToExprPath
    val SecretsContext.SIGNING_RELEASE_KEYALIAS by SecretsContext.propertyToExprPath
    val SecretsContext.SIGNING_RELEASE_KEYPASSWORD by SecretsContext.propertyToExprPath

    val SecretsContext.AWS_ACCESS_KEY_ID by SecretsContext.propertyToExprPath
    val SecretsContext.AWS_SECRET_ACCESS_KEY by SecretsContext.propertyToExprPath
    val SecretsContext.AWS_BASEURL by SecretsContext.propertyToExprPath
    val SecretsContext.AWS_REGION by SecretsContext.propertyToExprPath
    val SecretsContext.AWS_BUCKET by SecretsContext.propertyToExprPath
    val SecretsContext.DANDANPLAY_APP_ID by SecretsContext.propertyToExprPath
    val SecretsContext.DANDANPLAY_APP_SECRET by SecretsContext.propertyToExprPath
}

/// EXTENSIONS

val GitHubContext.isAnimekoRepository
    get() = """$repository == 'open-ani/animeko'"""

val GitHubContext.isPullRequest
    get() = """$event_name == 'pull_request'"""

val MatrixInstance.isX64 get() = arch == Arch.X64
val MatrixInstance.isAArch64 get() = arch == Arch.AARCH64

val MatrixInstance.isMacOS get() = os == OS.MACOS
val MatrixInstance.isWindows get() = os == OS.WINDOWS
val MatrixInstance.isUbuntu get() = os == OS.UBUNTU
val MatrixInstance.isUnix get() = (os == OS.UBUNTU) or (os == (OS.MACOS))

val MatrixInstance.isMacOSAArch64 get() = (os == OS.MACOS) and (arch == Arch.AARCH64)
val MatrixInstance.isMacOSX64 get() = (os == OS.MACOS) and (arch == Arch.X64)

// only for highlighting (though this does not work in KT 2.1.0)
fun shell(@Language("shell") command: String) = command

infix fun String.and(other: String) = "($this) && ($other)"
infix fun String.or(other: String) = "($this) || ($other)"

// 由于 infix 优先级问题, 这里要求使用传统调用方式.
fun String.eq(other: OS) = this.eq(other.toString())
fun String.eq(other: String) = "($this == '$other')"
fun String.eq(other: Boolean) = "($this == $other)"
fun String.neq(other: String) = "($this != '$other')"
fun String.neq(other: Boolean) = "($this != $other)"

operator fun String.not() = "!($this)"
