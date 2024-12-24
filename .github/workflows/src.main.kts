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
import io.github.typesafegithub.workflows.domain.ActionStep
import io.github.typesafegithub.workflows.domain.CommandStep
import io.github.typesafegithub.workflows.domain.JobOutputs
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.PullRequest
import io.github.typesafegithub.workflows.domain.triggers.Push
import io.github.typesafegithub.workflows.dsl.JobBuilder
import io.github.typesafegithub.workflows.dsl.expressions.Contexts
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

object OS {
    const val WINDOWS = "windows"
    const val UBUNTU = "ubuntu"
    const val MACOS = "macos"
}

object Arch {
    const val X64 = "x64"
    const val AARCH64 = "aarch64"
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

    val entriesWithoutUniversal = listOf(ARM64_V8A, X86_64, ARMEABI_V7A)
    val entriesWithUniversal = entriesWithoutUniversal + UNIVERSAL

    const val UNIVERSAL = "universal"
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
    val id: String,
    /**
     * 显示的名字, 不能变更, 否则会导致 PR Rules 失效
     */
    val name: String,
    /**
     * GitHub Actions 的规范名称, e.g. `ubuntu-20.04`, `windows-2019`.
     */
    val runsOn: List<String>,

    /**
     * 只在脚本内部判断 OS 使用, 不影响 github 调度机器
     * @see OS
     */
    val os: String,
    /**
     * 只在脚本内部判断 OS 使用, 不影响 github 调度机器
     * @see Arch
     */
    val arch: String,

    /**
     * `false` = GitHub Actions 的免费机器
     */
    val selfHosted: Boolean,
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

val matrixInstances = listOf(
    MatrixInstance(
        id = "windows-self-hosted",
        name = "Windows 10 x86_64",
        runsOn = listOf("self-hosted", "Windows", "X64"),
        os = OS.WINDOWS,
        arch = Arch.X64,
        selfHosted = true,
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
        id = "windows-2019",
        name = "Windows Server 2019 x86_64",
        runsOn = listOf("windows-2019"),
        os = OS.WINDOWS,
        arch = Arch.X64,
        selfHosted = false,
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
        id = "ubuntu-x64",
        name = "Ubuntu x86_64 (Compile only)",
        runsOn = listOf("ubuntu-20.04"),
        os = OS.UBUNTU,
        arch = Arch.X64,
        selfHosted = false,
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
        id = "macos-x64",
        name = "macOS x86_64",
        runsOn = listOf("macos-13"),
        os = OS.MACOS,
        arch = Arch.X64,
        selfHosted = false,
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
        id = "macos-aarch64",
        name = "macOS AArch64",
        runsOn = listOf("self-hosted", "macOS", "ARM64"),
        os = OS.MACOS,
        arch = Arch.AARCH64,
        selfHosted = true,
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


val buildJobBody: JobBuilder<JobOutputs.EMPTY>.() -> Unit = {
    uses(action = Checkout(submodules_Untyped = "recursive"))

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
    buildAndroidApk(prepareSigningKey)
    gradleCheck()
    uploadAnitorrent()
    packageDesktopAndUpload()
    cleanupTempFiles()
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
    job(
        id = "build",
        name = expr { matrix.name },
        runsOn = RunnerType.Custom(expr { matrix.runsOn }),
        _customArguments = generateStrategy(matrixInstances.filterNot { it.selfHosted }),
        block = buildJobBody,
    )

    job(
        id = "build_self_hosted",
        name = expr { matrix.name },
        runsOn = RunnerType.Custom(expr { matrix.runsOn }),
        `if` = expr { github.isAnimekoRepository },
        _customArguments = generateStrategy(matrixInstances.filter { it.selfHosted }),
        block = buildJobBody,
    )
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
    job(
        id = "build",
        name = expr { matrix.name },
        runsOn = RunnerType.Custom(expr { matrix.runsOn }),
        _customArguments = generateStrategy(matrixInstances.filterNot { it.selfHosted }),
        block = buildJobBody,
    )

    // No self-hosted for security. Only direct pushes to the repository branches will trigger the self-hosted jobs.
    // Organization members always push to a branch to create a fork and that will trigger a `Push` event that runs on self-hosted.
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

    val matrixInstancesForRelease = matrixInstances.filterNot { it.os == OS.UBUNTU }

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

        buildAndroidApk(prepareSigningKey)
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
        id = "release",
        name = expr { matrix.name },
        needs = listOf(createRelease),
        runsOn = RunnerType.Custom(expr { matrix.runsOn }),
        _customArguments = generateStrategy(matrixInstancesForRelease.filterNot { it.selfHosted }),
        block = jobBody,
    )
    job(
        id = "release_self_hosted",
        name = expr { matrix.name } + " (fork)",
        needs = listOf(createRelease),
        runsOn = RunnerType.Custom(expr { matrix.runsOn }),
        `if` = expr { github.isAnimekoRepository }, // Don't run on forks
        _customArguments = generateStrategy(matrixInstancesForRelease.filter { it.selfHosted }),
        block = jobBody,
    )
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
            append(expr { matrix.gradleArgs })
        },
    ),
    env = env,
)

/**
 * GitHub Actions 上给的硬盘比较少, 我们删掉一些不必要的文件来腾出空间.
 */
fun JobBuilder<*>.freeSpace() {
    run(
        name = "Free space for macOS",
        `if` = expr { matrix.isMacOS and !matrix.selfHosted },
        command = shell($$"""chmod +x ./ci-helper/free-space-macos.sh && ./ci-helper/free-space-macos.sh"""),
        continueOnError = true,
    )
}

fun JobBuilder<*>.installJbr21() {
    // For mac
    val jbrUrl = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-21.0.5-osx-aarch64-b631.8.tar.gz"
    val jbrChecksumUrl =
        "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-21.0.5-osx-aarch64-b631.8.tar.gz.checksum"

    val jbrFilename = jbrUrl.substringAfterLast('/')

    val jbrLocationExpr = run(
        name = "Resolve JBR location",
        `if` = expr { matrix.isMacOSAArch64 },
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
        `if` = expr { matrix.isMacOSAArch64 },
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
        `if` = expr { matrix.isMacOSAArch64 },
        action = SetupJava_Untyped(
            distribution_Untyped = "jdkfile",
            javaVersion_Untyped = "21",
            jdkFile_Untyped = expr { jbrLocationExpr },
        ),
        env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
    )

    // For Windows + Ubuntu
    uses(
        name = "Setup JBR 21 for other OS",
        `if` = expr { !matrix.isMacOSAArch64 },
        action = SetupJava_Untyped(
            distribution_Untyped = "jetbrains",
            javaVersion_Untyped = "21",
        ),
        env = mapOf("GITHUB_TOKEN" to expr { secrets.GITHUB_TOKEN }),
    )

    run(
        command = shell($$"""echo "jvm.toolchain.version=21" >> local.properties"""),
    )
}

fun JobBuilder<*>.installNativeDeps() {
    // Windows
    uses(
        name = "Setup vcpkg cache",
        `if` = expr { matrix.isWindows and matrix.installNativeDeps },
        action = GithubScript(
            script = """
                core.exportVariable('ACTIONS_CACHE_URL', process.env.ACTIONS_CACHE_URL || '');
                core.exportVariable('ACTIONS_RUNTIME_TOKEN', process.env.ACTIONS_RUNTIME_TOKEN || '');
            """.trimIndent(),
        ),
    )
    run(
        name = "Install Native Dependencies for Windows",
        `if` = expr { matrix.isWindows and matrix.installNativeDeps },
        command = "./ci-helper/install-deps-windows.cmd",
        env = mapOf("VCPKG_BINARY_SOURCES" to "clear;x-gha,readwrite"),
    )

    // MacOS
    run(
        name = "Install Native Dependencies for MacOS",
        `if` = expr { matrix.isMacOS and matrix.installNativeDeps },
        command = shell($$"""chmod +x ./ci-helper/install-deps-macos-ci.sh && ./ci-helper/install-deps-macos-ci.sh"""),
    )
}

fun JobBuilder<*>.chmod777() {
    run(
        `if` = expr { matrix.isUnix },
        command = "chmod -R 777 .",
    )
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
            command_Untyped = """./gradlew """ + expr { matrix.gradleArgs },
        ),
    )
}

fun JobBuilder<*>.prepareSigningKey(): ActionStep<Base64ToFile_Untyped.Outputs> {
    return uses(
        name = "Prepare signing key",
        `if` = expr { github.isAnimekoRepository and !github.isPullRequest and matrix.uploadApk },
        action = Base64ToFile_Untyped(
            fileName_Untyped = "android_signing_key",
            fileDir_Untyped = "./",
            encodedString_Untyped = expr { secrets.SIGNING_RELEASE_STOREFILE },
        ),
        continueOnError = true,
    )
}

fun JobBuilder<*>.buildAnitorrent() {
    runGradle(
        name = "Build Anitorrent for Desktop",
        `if` = expr { matrix.buildAnitorrent and matrix.buildAnitorrentSeparately },
        tasks = [
            ":torrent:anitorrent:build",
            ":torrent:anitorrent:anitorrent-native:buildAnitorrent",
        ],
    )

    runGradle(
        name = "Build Anitorrent for Android",
        `if` = expr { matrix.buildAnitorrent },
        tasks = [
            ":torrent:anitorrent:anitorrent-native:buildAnitorrent",
            "buildCMakeDebug",
            "buildCMakeRelWithDebInfo",
        ],
    )
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
    runGradle(
        name = "Build Android Debug APKs",
        `if` = expr { matrix.uploadApk },
        tasks = [
            "assembleDebug",
        ],
    )

    for (arch in AndroidArch.entriesWithUniversal) {
        uses(
            name = "Upload Android Debug APK $arch",
            `if` = if (arch == AndroidArch.UNIVERSAL) {
                expr { matrix.uploadApk and matrix.buildAllAndroidAbis }
            } else {
                expr { matrix.uploadApk }
            },
            action = UploadArtifact(
                name = "ani-android-${arch}-debug",
                path_Untyped = "app/android/build/outputs/apk/debug/android-${arch}-debug.apk",
                overwrite = true,
            ),
        )
    }

    runGradle(
        name = "Build Android Release APKs",
        `if` = expr { github.isAnimekoRepository and !github.isPullRequest and matrix.uploadApk },
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

    for (arch in AndroidArch.entriesWithUniversal) {
        uses(
            name = "Upload Android Release APK $arch",
            `if` = if (arch == AndroidArch.UNIVERSAL) {
                expr { matrix.uploadApk and matrix.buildAllAndroidAbis }
            } else {
                expr { matrix.uploadApk }
            },
            action = UploadArtifact(
                name = "ani-android-${arch}-release",
                path_Untyped = "app/android/build/outputs/apk/release/android-${arch}-release.apk",
                overwrite = true,
            ),
        )
    }
}

fun JobBuilder<*>.gradleCheck() {
    uses(
        name = "Check",
        `if` = expr { matrix.runTests },
        action = Retry_Untyped(
            maxAttempts_Untyped = "2",
            timeoutMinutes_Untyped = "60",
            command_Untyped = "./gradlew check " + expr { matrix.gradleArgs },
        ),
    )
}

fun JobBuilder<*>.uploadAnitorrent() {
    uses(
        name = "Upload Anitorrent CMakeCache.txt",
        `if` = expr { always() },
        action = UploadArtifact(
            name = $"anitorrent-cmake-cache-${expr { matrix.id }}",
            path_Untyped = "torrent/anitorrent/build-ci/CMakeCache.txt",
            overwrite = true,
        ),
    )
    uses(
        name = $"Upload Anitorrent ${expr { matrix.id }}",
        `if` = expr { always() },
        action = UploadArtifact(
            name = $"anitorrent-${expr { matrix.id }}",
            path_Untyped = "torrent/anitorrent/anitorrent-native/build",
            overwrite = true,
        ),
    )
}

fun JobBuilder<*>.packageDesktopAndUpload() {
    runGradle(
        name = "Package Desktop",
        `if` = expr { matrix.uploadDesktopInstallers and !matrix.isMacOSX64 },
        tasks = [
            "packageReleaseDistributionForCurrentOS",
        ],
    )

    uploadComposeLogs()

//    uses(
//        name = "Upload macOS portable",
//        `if` = expr { matrix.uploadDesktopInstallers and matrix.isMacOS },
//        action = UploadArtifact(
//            name = "ani-macos-portable-${expr { matrix.arch }}",
//            path_Untyped = "app/desktop/build/compose/binaries/main-release/app/Ani.app",
//        ),
//    )
    uses(
        name = "Upload macOS dmg",
        `if` = expr { matrix.uploadDesktopInstallers and matrix.isMacOS },
        action = UploadArtifact(
            name = "ani-macos-dmg-${expr { matrix.arch }}",
            path_Untyped = "app/desktop/build/compose/binaries/main-release/dmg/Ani-*.dmg",
            overwrite = true,
        ),
    )
    uses(
        name = "Upload Windows packages",
        `if` = expr { matrix.uploadDesktopInstallers and matrix.isWindows },
        action = UploadArtifact(
            name = "ani-windows-portable",
            path_Untyped = "app/desktop/build/compose/binaries/main-release/app",
            overwrite = true,
        ),
    )
}

fun JobBuilder<*>.uploadComposeLogs() {
    uses(
        name = "Upload compose logs",
        `if` = expr { matrix.uploadDesktopInstallers },
        action = UploadArtifact(
            name = "compose-logs-${expr { matrix.os }}-${expr { matrix.arch }}",
            path_Untyped = "app/desktop/build/compose/logs",
        ),
    )
}

class CIHelper(
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
        runGradle(
            name = "Upload Android APK for Release",
            `if` = expr { matrix.uploadApk },
            tasks = [":ci-helper:uploadAndroidApk"],
            env = ciHelperSecrets,
        )
    }

    fun JobBuilder<*>.generateQRCodeAndUpload() {
        val condition = expr { matrix.uploadApk and matrix.buildAllAndroidAbis }
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

    fun JobBuilder<*>.uploadDesktopInstallers() {
        runGradle(
            name = "Upload Desktop Installers",
            `if` = expr { matrix.uploadDesktopInstallers and (!matrix.isMacOSX64) },
            tasks = [":ci-helper:uploadDesktopInstallers"],
            env = ciHelperSecrets,
        )
    }
}

fun JobBuilder<*>.cleanupTempFiles() {
    run(
        name = "Cleanup temp files",
        `if` = expr { matrix.selfHosted and matrix.isMacOSAArch64 },
        command = shell("""chmod +x ./ci-helper/cleanup-temp-files-macos.sh && ./ci-helper/cleanup-temp-files-macos.sh"""),
        continueOnError = true,
    )
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

val Contexts.matrix get() = MatrixContext

val GitHubContext.isAnimekoRepository
    get() = """$repository == 'open-ani/animeko'"""

val GitHubContext.isPullRequest
    get() = """$event_name == 'pull_request'"""

val MatrixContext.isX64 get() = arch.eq(Arch.X64)
val MatrixContext.isAArch64 get() = arch.eq(Arch.AARCH64)

val MatrixContext.isMacOS get() = os.eq(OS.MACOS)
val MatrixContext.isWindows get() = os.eq(OS.WINDOWS)
val MatrixContext.isUbuntu get() = os.eq(OS.UBUNTU)
val MatrixContext.isUnix get() = (os.eq(OS.UBUNTU)) or (os.eq(OS.MACOS))

val MatrixContext.isMacOSAArch64 get() = (os.eq(OS.MACOS)) and (arch.eq(Arch.AARCH64))
val MatrixContext.isMacOSX64 get() = (os.eq(OS.MACOS)) and (arch.eq(Arch.X64))

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
            "id" to matrixInstances.map { it.id },
            "include" to matrixInstances.map { it.toMatrixIncludeMap() },
        ),
    ),
)