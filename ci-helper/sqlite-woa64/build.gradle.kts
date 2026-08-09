/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject

plugins {
    id("ani.base")
    `java-library`
}

// AndroidX sqlite-bundled-jvm 没有发布 Windows ARM64 native 库 (2.6.1 ~ 2.7.0 均无), WoA64 上
// BundledSQLiteDriver 会抛 "Cannot find a suitable SQLite binary for windows 11 | aarch64".
//
// 此模块在 Windows ARM64 主机上用 MSVC 编译出 sqliteJni.dll, 打成只含资源的 jar, 由 app-data 以
// runtimeOnly 引入. AndroidX 的 NativeLibraryLoader 通过 classloader 查找该资源, 所以只要它在
// runtime classpath 上就能解析, gradlew run / desktopTest / 打包 / 发版走同一条加载路径.
//
// 源码取自 SQLite 官方 amalgamation 和 androidx.sqlite 的 sqlite_bindings.cpp, 两者都以 sha256
// 钉死, 编译宏与 AndroidX 官方构建一致. 需要 Visual Studio 的 "C++ ARM64 build tools" 组件.
//
// AndroidX 官方发布 windows_arm64 后, 删除本目录、settings 中的模块注册和 app-data 那处 runtimeOnly 即可.

// JNI binding 取自 androidx.sqlite 2.6.1; androidx 不给 library 版本打 tag, 所以用该文件在
// 2.6.1 发版时点的 commit. 运行时实际解析到的版本由 Room 的传递依赖决定 (目前是 2.6.2),
// 与这里未必一致 —— JNI 这层在补丁版本间是稳定的, 实测 2.6.1 的 binding 在 2.6.2 上工作正常.
// 若日后升级 androidx.sqlite 后出现加载或行为异常, 把下面三个值换成新版本对应的 commit.
val bindingSqliteVersion = "2.6.1"
val bindingCommit = "ce10e55447f4e0fd21a9001d0589a6e1e7a5a8d7"
val bindingSha256 = "4C535DFF9D2E30E8B0B5203455167020E102185E22F3056A27DA0B1AB71D3897"

val sqliteVersion = "3.50.1"
val sqliteAmalgamationVersion = "3500100"
val sqliteAmalgamationYear = "2025"
val sqliteAmalgamationSha256 = "41716B44AC8777188C4C3F1F370F01C9CB9E3B6428EB5C981D086C35DE2D9D3F"

// 判断的是构建主机: 这里是原生编译, 无法从 x64 交叉构建, 所以其他平台上本模块只产出空 jar (无人依赖).
val isWindowsArm64Host = getOs() == Os.Windows && getArch() == Arch.AARCH64

/**
 * 下载单个文件并校验 sha256. [base64Encoded] 用于 googlesource 的 `?format=TEXT` 接口.
 */
abstract class DownloadAndVerify : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:Input
    abstract val base64Encoded: Property<Boolean>

    @get:OutputFile
    abstract val target: RegularFileProperty

    @TaskAction
    fun run() {
        val url = url.get()
        var downloaded: ByteArray? = null
        var lastError: Exception? = null
        for (attempt in 1..5) {
            try {
                downloaded = URI(url).toURL().openStream().use { it.readBytes() }
                break
            } catch (e: Exception) {
                lastError = e
                logger.warn("Downloading $url failed on attempt $attempt/5: ${e.message}")
                if (attempt < 5) {
                    Thread.sleep(minOf(60_000L, 5_000L * (1L shl attempt)))
                }
            }
        }
        val bytes = downloaded ?: throw GradleException("Failed to download $url", lastError)
        val content = if (base64Encoded.get()) {
            Base64.getMimeDecoder().decode(String(bytes, Charsets.US_ASCII).trim())
        } else {
            bytes
        }

        val actual = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02X".format(it) }
        if (!actual.equals(sha256.get(), ignoreCase = true)) {
            throw GradleException("SHA256 mismatch for $url. Expected ${sha256.get()}, got $actual.")
        }

        val targetFile = target.get().asFile
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(content)
    }
}

/**
 * 用 MSVC 的 arm64 工具链把 amalgamation 和 JNI binding 编译成 `sqliteJni.dll`.
 */
abstract class CompileSqliteJni @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val amalgamationDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bindingSource: RegularFileProperty

    @get:Input
    abstract val expectedSqliteVersion: Property<String>

    @get:Input
    abstract val javaHome: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val amalgamationDir = amalgamationDirectory.get().asFile
        val expected = expectedSqliteVersion.get()
        val header = amalgamationDir.resolve("sqlite3.h").readText()
        if (!Regex("""#define\s+SQLITE_VERSION\s+"${Regex.escape(expected)}"""").containsMatchIn(header)) {
            throw GradleException("Downloaded SQLite amalgamation does not match $expected.")
        }

        val vcvars = findVisualStudioWithArm64Tools().resolve("VC\\Auxiliary\\Build\\vcvarsall.bat")
        if (!vcvars.isFile) {
            throw GradleException("vcvarsall.bat was not found at $vcvars.")
        }

        val javaHomeDir = File(javaHome.get())
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()
        val objDir = temporaryDir
        val dll = outputDir.resolve("sqliteJni.dll")
        val sqliteObj = objDir.resolve("sqlite3.obj")
        val bindingObj = objDir.resolve("sqlite_bindings.obj")

        // 与 AndroidX 官方构建保持一致的编译宏
        val defines = listOf(
            "HAVE_USLEEP=1",
            "SQLITE_DEFAULT_AUTOVACUUM=1",
            "SQLITE_DEFAULT_MEMSTATUS=0",
            "SQLITE_DEFAULT_WAL_SYNCHRONOUS=1",
            "SQLITE_ENABLE_COLUMN_METADATA",
            "SQLITE_ENABLE_FTS3",
            "SQLITE_ENABLE_FTS3_PARENTHESIS",
            "SQLITE_ENABLE_FTS4",
            "SQLITE_ENABLE_FTS5",
            "SQLITE_ENABLE_JSON1",
            "SQLITE_ENABLE_MATH_FUNCTIONS",
            "SQLITE_ENABLE_NORMALIZE",
            "SQLITE_ENABLE_RTREE",
            "SQLITE_ENABLE_STAT4",
            "SQLITE_HAVE_ISNAN",
            "SQLITE_OMIT_BUILTIN_TEST",
            "SQLITE_OMIT_DEPRECATED",
            "SQLITE_OMIT_PROGRESS_CALLBACK",
            "SQLITE_OMIT_SHARED_CACHE",
            "SQLITE_SECURE_DELETE",
            "SQLITE_TEMP_STORE=3",
            "SQLITE_THREADSAFE=2",
        ).joinToString(" ") { "/D$it" }

        // /Brepro 让产物可复现, /MT 静态链接 CRT 以免依赖目标机器的 VC++ 运行库.
        // 命令写进 .cmd 再执行: 直接传给 cmd.exe /c 的话, 命令串里的引号会被 Windows 的参数规则拆散.
        val script = objDir.resolve("build-sqlite-jni.cmd")
        script.writeText(
            listOf(
                "@echo off",
                "call \"${vcvars.absolutePath}\" arm64 || exit /b 1",
                "cl /nologo /O2 /Brepro /MT /utf-8 $defines " +
                        "/I\"${amalgamationDir.absolutePath}\" " +
                        "/Fo\"${sqliteObj.absolutePath}\" " +
                        "/c \"${amalgamationDir.resolve("sqlite3.c").absolutePath}\" || exit /b 1",
                "cl /nologo /O2 /Brepro /MT /EHsc /std:c++17 /utf-8 $defines " +
                        "/I\"${amalgamationDir.absolutePath}\" " +
                        "/I\"${javaHomeDir.resolve("include").absolutePath}\" " +
                        "/I\"${javaHomeDir.resolve("include\\win32").absolutePath}\" " +
                        "/Fo\"${bindingObj.absolutePath}\" " +
                        "/c \"${bindingSource.get().asFile.absolutePath}\" || exit /b 1",
                "link /nologo /Brepro /DLL /OUT:\"${dll.absolutePath}\" " +
                        "/IMPLIB:\"${objDir.resolve("sqliteJni.lib").absolutePath}\" " +
                        "\"${sqliteObj.absolutePath}\" \"${bindingObj.absolutePath}\" || exit /b 1",
            ).joinToString("\r\n", postfix = "\r\n"),
        )

        execOperations.exec {
            commandLine("cmd.exe", "/d", "/s", "/c", script.absolutePath)
        }
    }

    private fun findVisualStudioWithArm64Tools(): File {
        val vswhere = File(
            System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)",
            "Microsoft Visual Studio\\Installer\\vswhere.exe",
        )
        if (!vswhere.isFile) {
            throw GradleException(
                "vswhere.exe was not found at $vswhere. " +
                        "Install Visual Studio with the C++ ARM64 build tools.",
            )
        }
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(
                vswhere.absolutePath,
                "-latest", "-products", "*",
                "-requires", "Microsoft.VisualStudio.Component.VC.Tools.ARM64",
                "-property", "installationPath",
            )
            standardOutput = output
        }
        val path = output.toString(Charsets.UTF_8.name()).trim()
        if (path.isEmpty()) {
            throw GradleException(
                "Visual Studio ARM64 C++ tools were not found. " +
                        "Install the \"MSVC v143 - VS 2022 C++ ARM64 build tools\" component.",
            )
        }
        return File(path)
    }
}

val downloadSqliteAmalgamation = tasks.register<DownloadAndVerify>("downloadSqliteAmalgamation") {
    description = "Downloads the SQLite $sqliteVersion amalgamation source archive"
    url = "https://www.sqlite.org/$sqliteAmalgamationYear/sqlite-amalgamation-$sqliteAmalgamationVersion.zip"
    sha256 = sqliteAmalgamationSha256
    base64Encoded = false
    target = layout.buildDirectory.file("sqlite-woa64/sqlite-amalgamation-$sqliteAmalgamationVersion.zip")
}

val unzipSqliteAmalgamation = tasks.register<Sync>("unzipSqliteAmalgamation") {
    description = "Unpacks the SQLite amalgamation source archive"
    from(zipTree(downloadSqliteAmalgamation.flatMap { it.target })) {
        // 去掉压缩包里的顶层 sqlite-amalgamation-<version>/ 目录
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("sqlite-woa64/amalgamation"))
}

val downloadSqliteBinding = tasks.register<DownloadAndVerify>("downloadSqliteBinding") {
    description = "Downloads sqlite_bindings.cpp of androidx.sqlite $bindingSqliteVersion"
    url = "https://android.googlesource.com/platform/frameworks/support/+/$bindingCommit" +
            "/sqlite/sqlite-bundled/src/jvmAndroidMain/jni/sqlite_bindings.cpp?format=TEXT"
    sha256 = bindingSha256
    base64Encoded = true
    target = layout.buildDirectory.file("sqlite-woa64/sqlite_bindings.cpp")
}

// 只在 Windows ARM64 主机上注册编译任务并接进 jar: 其他平台既编不出来, 也没有人依赖这个 jar.
if (isWindowsArm64Host) {
    val compileSqliteJni = tasks.register<CompileSqliteJni>("compileSqliteJni") {
        description = "Builds natives/windows_arm64/sqliteJni.dll for androidx.sqlite $bindingSqliteVersion"
        amalgamationDirectory = layout.dir(unzipSqliteAmalgamation.map { it.destinationDir })
        bindingSource = downloadSqliteBinding.flatMap { it.target }
        expectedSqliteVersion = sqliteVersion
        javaHome = providers.environmentVariable("JAVA_HOME")
            .orElse(providers.systemProperty("java.home"))
        outputDirectory = layout.buildDirectory.dir("sqlite-woa64/native")
    }

    tasks.jar {
        from(compileSqliteJni.flatMap { it.outputDirectory.file("sqliteJni.dll") }) {
            into("natives/windows_arm64")
        }
    }
}
