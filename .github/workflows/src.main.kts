#!/usr/bin/env kotlin

/*
 * Copyright (C) 2024 OpenAni and contributors.
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
@file:DependsOn("nick-fields:retry:v2")
@file:DependsOn("timheuer:base64-to-file:v1.1")
@file:DependsOn("actions:upload-artifact:v4")

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
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.GithubScript
import io.github.typesafegithub.workflows.actions.actions.UploadArtifact
import io.github.typesafegithub.workflows.actions.bhowell2.GithubSubstringAction_Untyped
import io.github.typesafegithub.workflows.actions.dawidd6.ActionGetTag_Untyped
import io.github.typesafegithub.workflows.actions.gmitch215.SetupJava_Untyped
import io.github.typesafegithub.workflows.actions.gradle.ActionsSetupGradle
import io.github.typesafegithub.workflows.actions.nickfields.Retry_Untyped
import io.github.typesafegithub.workflows.actions.snowactions.Qrcode_Untyped
import io.github.typesafegithub.workflows.actions.softprops.ActionGhRelease
import io.github.typesafegithub.workflows.actions.timheuer.Base64ToFile_Untyped
import io.github.typesafegithub.workflows.domain.AbstractResult
import io.github.typesafegithub.workflows.domain.ActionStep
import io.github.typesafegithub.workflows.domain.CommandStep
import io.github.typesafegithub.workflows.domain.Job
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.Step
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.expressions.ExpressionContext
import io.github.typesafegithub.workflows.dsl.expressions.contexts.GitHubContext
import io.github.typesafegithub.workflows.dsl.expressions.contexts.SecretsContext
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig
import org.intellij.lang.annotations.Language
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties

check(KotlinVersion.CURRENT.isAtLeast(2, 0, 0)) {
    "This script requires Kotlin 2.0.0 or later"
}

enum class OS {
    windows,
    ubuntu,
    macos;

    companion object {
        val WINDOWS get() = windows
        val UBUNTU get() = ubuntu
        val MACOS get() = macos
    }
}

enum class Arch {
    x64,
    aarch64;

    companion object {
        val X64 get() = x64
        val AARCH64 get() = aarch64
    }
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
/**
 * 一台机器的配置
 *
 * 如果改了, 也要改 [MatrixContext]
 */
class MatrixInstance(
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
    val buildAnitorrent: Boolean,
    val buildAnitorrentSeparately: Boolean,
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
    gradleHeap: String = "4g",
    kotlinCompilerHeap: String = "4g",
    /**
     * 只能在内存比较大的时候用.
     */
    gradleParallel: Boolean = selfHosted,
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
        add(quote("--no-configuration-cache"))
        add(quote("-Porg.gradle.daemon.idletimeout=60000"))
        add(quote("-Pkotlin.native.ignoreDisabledTargets=true"))
        add(quote("-Dfile.encoding=UTF-8"))

        if (buildAnitorrent) {
            add(quote("-Dani.enable.anitorrent=true"))
            add(quote("-DCMAKE_BUILD_TYPE=Release"))
        }

        if (os == OS.WINDOWS) {
            add(quote("-DCMAKE_TOOLCHAIN_FILE=C:/vcpkg/scripts/buildsystems/vcpkg.cmake"))
            add(quote("-DBoost_INCLUDE_DIR=C:/vcpkg/installed/x64-windows/include"))
        }

        add(quote("-Dorg.gradle.jvmargs=-Xmx${gradleHeap}"))
        add(quote("-Dkotlin.daemon.jvm.options=-Xmx${kotlinCompilerHeap}"))

        if (gradleParallel) {
            add(quote("--parallel"))
        }

        extraGradleArgs.forEach {
            add(quote(it))
        }
    }.joinToString(" ")

    init {
        require(os in listOf(OS.WINDOWS, OS.UBUNTU, OS.MACOS)) { "Unsupported OS: $os" }
        require(arch in listOf(Arch.X64, Arch.AARCH64)) { "Unsupported arch: $arch" }

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

    object GithubUbuntu2004 : GithubHosted(
        id = "github-ubuntu-2004",
        displayName = "Ubuntu 20.04 x86_64 (GitHub)",
        os = OS.UBUNTU,
        arch = Arch.X64,
        labels = setOf("ubuntu-20.04"),
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
//            GithubUbuntu2004,
//            SelfHostedWindows10,
//            SelfHostedMacOS15,
//        )
//    }

    override fun toString(): String = id
}

val Runner.isSelfHosted: Boolean
    get() = this is Runner.SelfHosted

// Machines for Build and Release
val buildMatrixInstances = listOf(
    MatrixInstance(
        runner = Runner.SelfHostedWindows10,
        uploadApk = false,
        buildAnitorrent = true,
        buildAnitorrentSeparately = false, // windows 单线程构建 anitorrent, 要一起跑节约时间
        composeResourceTriple = "windows-x64",
        gradleHeap = "6g",
        kotlinCompilerHeap = "6g",
        gradleParallel = true,
        uploadDesktopInstallers = false, // 只有 win server 2019 构建的包才能正常使用 anitorrent
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=x86_64",
        ),
        buildAllAndroidAbis = false,
    ),
    MatrixInstance(
        runner = Runner.GithubWindowsServer2019,
        name = "Windows Server 2019 x86_64",
        uploadApk = false,
        buildAnitorrent = true,
        buildAnitorrentSeparately = false, // windows 单线程构建 anitorrent, 要一起跑节约时间
        composeResourceTriple = "windows-x64",
        gradleHeap = "4g",
        kotlinCompilerHeap = "4g",
        gradleParallel = true,
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=x86_64",
        ),
        buildAllAndroidAbis = false,
    ),
    MatrixInstance(
        runner = Runner.GithubUbuntu2004,
        name = "Ubuntu x86_64 (Compile only)",
        uploadApk = false,
        buildAnitorrent = false,
        buildAnitorrentSeparately = false,
        composeResourceTriple = "linux-x64",
        runTests = false,
        uploadDesktopInstallers = false,
        extraGradleArgs = listOf(),
        gradleHeap = "4g",
        kotlinCompilerHeap = "4g",
        buildAllAndroidAbis = true,
    ),
    MatrixInstance(
        runner = Runner.GithubMacOS13,
        uploadApk = true, // all ABIs
        buildAnitorrent = true,
        buildAnitorrentSeparately = true,
        composeResourceTriple = "macos-x64",
        buildIosFramework = false,
        gradleHeap = "4g",
        kotlinCompilerHeap = "4g",
        extraGradleArgs = listOf(),
        buildAllAndroidAbis = true,
    ),
    MatrixInstance(
        runner = Runner.SelfHostedMacOS15,
        uploadApk = true, // upload arm64-v8a once finished
        buildAnitorrent = true,
        buildAnitorrentSeparately = true,
        composeResourceTriple = "macos-arm64",
        extraGradleArgs = listOf(
            "-P$ANI_ANDROID_ABIS=arm64-v8a",
        ),
        buildIosFramework = false,
        gradleHeap = "6g",
        kotlinCompilerHeap = "4g",
        gradleParallel = true,
        buildAllAndroidAbis = false,
    ),
)

class VerifyMatrixInstance(
    val id: String,
    val name: String,
    val runsOn: List<String>,
) {
//    return MatrixInstance(
//        id = id,
//        name = name,
//        runsOn = runsOn,
//
//        // The following arguments are not used.
//        os = OS.WINDOWS,
//        arch = Arch.X64,
//        selfHosted = false,
//        uploadApk = false,
//        buildAnitorrent = true,
//        buildAnitorrentSeparately = false,
//        composeResourceTriple = "windows-x64",
//        gradleHeap = "4g",
//        kotlinCompilerHeap = "4g",
//        gradleParallel = true,
//        extraGradleArgs = listOf(),
//        buildAllAndroidAbis = true,
//    )
}

val verifyMatrixInstancesGithub = listOf(
    VerifyMatrixInstance(
        id = "github-windows-2019",
        name = "Windows Server 2019 x86_64 (GitHub)",
        runsOn = listOf("windows-2019"),
    ),
    VerifyMatrixInstance(
        id = "github-windows-2022",
        name = "Windows Server 2022 x86_64 (GitHub)",
        runsOn = listOf("windows-2022"),
    ),
//    VerifyMatrixInstance(
//        id = "github-macos-13",
//        name = "macOS 13 x86_64 (GitHub)",
//        runsOn = listOf("macos-13"),
//    ),
    VerifyMatrixInstance(
        id = "github-macos-14",
        name = "macOS 14 AArch64 (GitHub)",
        runsOn = listOf("macos-14"),
    ),
)

val verifyMatrixInstancesSelfHosted = listOf(
    VerifyMatrixInstance(
        id = "self-hosted-windows-10",
        name = "Windows 10 x86_64 (Self-Hosted)",
        runsOn = listOf("self-hosted", "Windows", "X64"),
    ),
    VerifyMatrixInstance(
        id = "self-hosted-macos-15",
        name = "macOS 15 AArch64 (Self-Hosted)",
        runsOn = listOf("self-hosted", "macOS", "ARM64"),
    ),
)

class BuildJobOutputs : JobOutputs() {
    var macosAarch64DmgSuccess by output()
    var macosAarch64DmgUrl by output()
    var windowsX64PortableSuccess by output()
    var windowsX64PortableUrl by output()
}

fun getBuildJobBody(matrix: MatrixInstance): JobBuilder<BuildJobOutputs>.() -> Unit = {
    uses(action = Checkout(submodules_Untyped = "recursive"))

    with(WithMatrix(matrix)) {
        freeSpace()
        installJbr21()
        installNativeDeps()
        chmod777()
        setupGradle()

        runGradle(
            name = "Update dev version name",
            tasks = ["updateDevVersionNameFromGit"],
        )

        val prepareSigningKey = prepareSigningKey()
        buildAnitorrent()
        compileAndAssemble()
        prepareSigningKey?.let {
            buildAndroidApk(it)
        }
        gradleCheck()
        uploadAnitorrent()
        val packageOutputs = packageDesktopAndUpload()

        packageOutputs.macosAarch64DmgOutcome?.let {
            jobOutputs.macosAarch64DmgSuccess = it.eq(AbstractResult.Status.Success)
            jobOutputs.macosAarch64DmgUrl = packageOutputs.macosAarch64DmgUrl.orEmpty()
        }

        packageOutputs.windowsX64PortableOutcome?.let {
            jobOutputs.windowsX64PortableSuccess = it.eq(AbstractResult.Status.Success)
            jobOutputs.windowsX64PortableUrl = packageOutputs.windowsX64PortableUrl.orEmpty()
        }

        cleanupTempFiles()
    }
}

fun getVerifyJobBody(
    buildJobOutputs: BuildJobOutputs,
    os: OS,
    arch: Arch
): JobBuilder<JobOutputs.EMPTY>.() -> Unit = {
    uses(action = Checkout()) // not recursive

    class VerifyTask(
        val name: String,
        val step: String,
    )

    val tasksToExecute = listOf(
        VerifyTask(
            name = "anitorrent-load-test",
            step = "Check that Anitorrent can be loaded",
        ),
    )

    when (os to arch) {
        OS.WINDOWS to Arch.X64 -> {
            // TODO
        }

        OS.MACOS to Arch.AARCH64 -> {
            // macos aarch64
            kotlin.run {
                run(
                    name = $$"Download ani.dmg",
                    command = shell(
                        // Include GITHUB_TOKEN
                        $$"""curl -H "Authorization: Bearer $GITHUB_TOKEN" -L "$URL" -o ani.dmg""",
                    ),
                    env = mapOf(
                        "GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN },
                        "URL" to buildJobOutputs.macosAarch64DmgUrl,
                    ),
                )

                tasksToExecute.forEach { task ->
                    run(
                        name = task.step,
                        command = shell($$"""./ci-helper/run-ani-test-macos-aarch64.sh ani.dmg $${task.name}"""),
                    )
                }
            }
        }

        else -> error("Unsupported OS and arch combination: $os $arch")
    }
}


workflow(
    name = "Build",
    on = listOf(
        // Including: 
        // - pushing directly to main
        // - pushing to a branch that has an associated PR
        Push(pathsIgnore = listOf("**/*.md")),
    ),
    sourceFile = __FILE__,
    targetFileName = "build.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
) {
    fun addVerifyJob(build: Job<BuildJobOutputs>, runner: Runner, ifExpr: String) {
        job(
            id = "verify_${runner.id}",
            name = """Verify (${runner.name})""",
            needs = listOf(build),
            `if` = if (runner.isSelfHosted) {
                expr { github.isAnimekoRepository and ifExpr }
            } else {
                expr { ifExpr }
            },
            runsOn = RunnerType.Labelled(runner.labels),
            block = getVerifyJobBody(build.outputs, runner.os, runner.arch),
        )
    }

    // Expands job matrix at compile-time so that we set job-level `if` condition. 
    val builds: List<Pair<MatrixInstance, Job<BuildJobOutputs>>> = buildMatrixInstances.map { matrix ->
        matrix to job(
            id = "build_${matrix.runner.id}",
            name = """Build (${matrix.name})""",
            runsOn = RunnerType.Labelled(matrix.runsOn),
            `if` = if (matrix.selfHosted) {
                expr { github.isAnimekoRepository }
            } else {
                null // always
            },
            outputs = BuildJobOutputs(),
            block = getBuildJobBody(matrix),
        )
    }

    builds[Runner.GithubWindowsServer2019].let { build ->
        listOf(
            Runner.GithubWindowsServer2019,
            Runner.GithubWindowsServer2022,
            Runner.SelfHostedWindows10,
        ).forEach { runner ->
            addVerifyJob(build, runner, build.outputs.windowsX64PortableSuccess)
        }
    }
    builds[Runner.SelfHostedMacOS15].let { build ->
        listOf(
            Runner.SelfHostedMacOS15,
            Runner.GithubMacOS14,
        ).forEach { runner ->
            addVerifyJob(build, runner, build.outputs.macosAarch64DmgSuccess)
        }
    }
}

operator fun List<Pair<MatrixInstance, Job<BuildJobOutputs>>>.get(runner: Runner): Job<BuildJobOutputs> {
    return first { it.first.runner == runner }.second
}

workflow(
    name = "Build",
    on = listOf(
        PullRequest(pathsIgnore = listOf("**/*.md")),
    ),
    sourceFile = __FILE__,
    targetFileName = "build_pr.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
) {
    buildMatrixInstances.filterNot { it.selfHosted }.map { matrix ->
        matrix to job(
            id = "build_${matrix.runner.id}",
            name = """Build (${matrix.name})""",
            runsOn = RunnerType.Labelled(matrix.runsOn),
            outputs = BuildJobOutputs(),
            block = getBuildJobBody(matrix),
        )
    }

    // No self-hosted for security. Only direct pushes to the repository branches will trigger the self-hosted jobs.
    // Organization members always push to a branch to create a fork and that will trigger a `Push` event that runs on self-hosted.

    // TODO verify
}

workflow(
    name = "Release",
    on = listOf(
        // Only commiter with write-access can trigger this
        Push(tags = listOf("v*")),
    ),
    sourceFile = __FILE__,
    targetFileName = "release.yml",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
) {
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

    val matrixInstancesForRelease = buildMatrixInstances.filterNot { it.os == OS.UBUNTU }

    fun addJob(matrix: MatrixInstance) = with(WithMatrix(matrix)) {
        val jobBody: JobBuilder<JobOutputs.EMPTY>.() -> Unit = {
            uses(action = Checkout(submodules_Untyped = "recursive"))

            val gitTag = getGitTag()

            freeSpace()
            installJbr21()
            installNativeDeps()
            chmod777()
            setupGradle()

            runGradle(
                name = "Update Release Version Name",
                tasks = ["updateReleaseVersionNameFromGit"],
                env = mapOf(
                    "GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN },
                    "GITHUB_REPOSITORY" to expr { secrets.GITHUB_REPOSITORY },
                    "CI_RELEASE_ID" to expr { createRelease.outputs.id },
                    "CI_TAG" to expr { gitTag.tagExpr },
                ),
            )

            val prepareSigningKey = prepareSigningKey()
            buildAnitorrent()
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
    ): CommandStep = run(
        name = name,
        `if` = `if`,
        command = shell(
            buildString {
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
    }

    fun JobBuilder<*>.installJbr21() {
        // For mac
        val jbrUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-21.0.5-osx-aarch64-b631.8.tar.gz"
        val jbrChecksumUrl =
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-21.0.5-osx-aarch64-b631.8.tar.gz.checksum"

        val jbrFilename = jbrUrl.substringAfterLast('/')

        when (matrix.runner.os to matrix.runner.arch) {
            OS.MACOS to Arch.AARCH64 -> {
                val jbrLocationExpr = run(
                    name = "Resolve JBR location",
                    command = shell(
                        $$"""
            # Expand jbrLocationExpr
            jbr_location_expr=$(eval echo $${expr { runner.tool_cache } + "/" + jbrFilename})
            echo "jbrLocation=$jbr_location_expr" >> $GITHUB_OUTPUT
            """.trimIndent(),
                    ),
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
                )

                uses(
                    name = "Setup JBR 21 for macOS AArch64",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "jdkfile",
                        javaVersion_Untyped = "21",
                        jdkFile_Untyped = expr { jbrLocationExpr },
                    ),
                    env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
                )
            }

            else -> {
                // For Windows + Ubuntu
                uses(
                    name = "Setup JBR 21 for other OS",
                    action = SetupJava_Untyped(
                        distribution_Untyped = "jetbrains",
                        javaVersion_Untyped = "21",
                    ),
                    env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
                )
            }
        }

        run(
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
                command_Untyped = """./gradlew """ + matrix.gradleArgs,
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

    fun JobBuilder<*>.buildAnitorrent() {
        if (matrix.buildAnitorrent and matrix.buildAnitorrentSeparately) {
            runGradle(
                name = "Build Anitorrent for Desktop",
                tasks = [
                    ":torrent:anitorrent:build",
                    ":torrent:anitorrent:anitorrent-native:buildAnitorrent",
                ],
            )
        }

        if (matrix.buildAnitorrent) {
            runGradle(
                name = "Build Anitorrent for Android",
                tasks = [
                    ":torrent:anitorrent:anitorrent-native:buildAnitorrent",
                    "buildCMakeDebug",
                    "buildCMakeRelWithDebInfo",
                ],
            )
        }
    }

    fun JobBuilder<*>.compileAndAssemble() {
        runGradle(
            name = "Compile Kotlin",
            tasks = [
                "compileKotlin",
                "compileCommonMainKotlinMetadata",
                "compileDebugKotlinAndroid",
                "compileReleaseKotlinAndroid",
                "compileJvmMainKotlinMetadata",
                "compileKotlinDesktop",
                "compileKotlinMetadata",
            ],
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
            uses(
                name = "Check",
                action = Retry_Untyped(
                    maxAttempts_Untyped = "2",
                    timeoutMinutes_Untyped = "60",
                    command_Untyped = "./gradlew check " + matrix.gradleArgs,
                ),
            )
        }
    }

    fun JobBuilder<*>.uploadAnitorrent() {
        uses(
            name = "Upload Anitorrent CMakeCache.txt",
            `if` = expr { always() },
            action = UploadArtifact(
                name = $"anitorrent-cmake-cache-${matrix.runner.id}",
                path_Untyped = "torrent/anitorrent/build-ci/CMakeCache.txt",
                overwrite = true,
            ),
        )
        uses(
            name = $"Upload Anitorrent ${matrix.runner.id}",
            `if` = expr { always() },
            action = UploadArtifact(
                name = $"anitorrent-${matrix.runner.id}",
                path_Untyped = "torrent/anitorrent/anitorrent-native/build",
                overwrite = true,
            ),
        )
    }

    class PackageDesktopAndUploadOutputs {
        // null means not enabled on this machine
        var macosAarch64DmgOutcome: Step<*>.Outcome? = null
        var macosAarch64DmgUrl: String? = null
        var windowsX64PortableOutcome: Step<*>.Outcome? = null
        var windowsX64PortableUrl: String? = null
    }

    fun JobBuilder<*>.packageDesktopAndUpload(): PackageDesktopAndUploadOutputs {
        if (matrix.uploadDesktopInstallers and !matrix.isMacOSX64) {
            runGradle(
                name = "Package Desktop",
                tasks = [
                    "packageReleaseDistributionForCurrentOS",
                ],
            )
        }

        uploadComposeLogs()

//    uses(
//        name = "Upload macOS portable",
//        `if` = matrix.uploadDesktopInstallers and matrix.isMacOS,
//        action = UploadArtifact(
//            name = "ani-macos-portable-${matrix.arch}",
//            path_Untyped = "app/desktop/build/compose/binaries/main-release/app/Ani.app",
//        ),
//    )
        return PackageDesktopAndUploadOutputs().apply {
            if (matrix.uploadDesktopInstallers and matrix.isMacOS) {
                val macosAarch64Dmg = uses(
                    name = "Upload macOS dmg",
                    action = UploadArtifact(
                        name = "ani-macos-dmg-${matrix.arch}",
                        path_Untyped = "app/desktop/build/compose/binaries/main-release/dmg/Ani-*.dmg",
                        overwrite = true,
                    ),
                )

                this.macosAarch64DmgOutcome = macosAarch64Dmg.outcome
                this.macosAarch64DmgUrl = macosAarch64Dmg.outputs.artifactUrl
            }

            if (matrix.uploadDesktopInstallers and matrix.isWindows) {
                val windowsX64Portable = uses(
                    name = "Upload Windows packages",
                    action = UploadArtifact(
                        name = "ani-windows-portable",
                        path_Untyped = "app/desktop/build/compose/binaries/main-release/app",
                        overwrite = true,
                    ),
                )

                this.windowsX64PortableOutcome = windowsX64Portable.outcome
                this.windowsX64PortableUrl = windowsX64Portable.outputs.artifactUrl
            }
        }
    }

    fun JobBuilder<*>.uploadComposeLogs() {
        if (matrix.uploadDesktopInstallers) {
            uses(
                name = "Upload compose logs",
                action = UploadArtifact(
                    name = "compose-logs-${matrix.os}-${matrix.arch}",
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
                    tasks = [":ci-helper:uploadAndroidApk"],
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
                    tasks = [":ci-helper:uploadAndroidApkQR"],
                    env = ciHelperSecrets,
                )
            }
        }

        fun JobBuilder<*>.uploadDesktopInstallers() {
            if (matrix.uploadDesktopInstallers and (!matrix.isMacOSX64)) {
                runGradle(
                    name = "Upload Desktop Installers",
                    tasks = [":ci-helper:uploadDesktopInstallers"],
                    env = ciHelperSecrets,
                )
            }
        }
    }
}

/// ENV

object MatrixContext : ExpressionContext("matrix") {
    val id by propertyToExprPath
    val os by propertyToExprPath
    val runsOn by propertyToExprPath
    val selfHosted by propertyToExprPath
    val installNativeDeps by propertyToExprPath
    val name by propertyToExprPath
    val uploadApk by propertyToExprPath
    val arch by propertyToExprPath
    val buildAnitorrent by propertyToExprPath
    val buildAnitorrentSeparately by propertyToExprPath
    val composeResourceTriple by propertyToExprPath
    val runTests by propertyToExprPath
    val uploadDesktopInstallers by propertyToExprPath
    val buildAllAndroidAbis by propertyToExprPath
    val gradleArgs by propertyToExprPath

    init {
        // check properties exists
        val instanceProperties = MatrixInstance::class.memberProperties
        val allowedNames = instanceProperties.map { it.name }
        MatrixContext::class.declaredMemberProperties.forEach {
            check(it.name in allowedNames) { "Property ${it.name} not found in MatrixInstance" }
        }
    }
}

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

fun MatrixInstance.toMatrixIncludeMap(): Map<String, Any> {
    @Suppress("UNCHECKED_CAST")
    val memberProperties =
        this::class.memberProperties as Collection<KProperty1<MatrixInstance, *>>

    return buildMap {
        for (property in memberProperties) {
            val value = property.get(this@toMatrixIncludeMap)
            if (value != null) {
                put(property.name, value)
            }
        }
    }
}

fun generateStrategy(matrixInstances: List<MatrixInstance>) = mapOf(
    "strategy" to mapOf(
        "fail-fast" to false,
        "matrix" to mapOf(
            "id" to matrixInstances.map { it.runner },
            "include" to matrixInstances.map { it.toMatrixIncludeMap() },
        ),
    ),
)