/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.Base64
import javax.inject.Inject


/**
 * 构建 `xcodebuild` 命令行参数的工具对象。
 *
 * 将 workspace、scheme、destination、sdk 等 Xcode 构建参数组装成一个完整的命令行参数列表,
 * 供 [XcodeArchiveTask] 和模拟器构建等场景使用。
 */
object IpaArguments {
    /**
     * 创建 `xcodebuild` 命令行参数列表。
     *
     * @param workspace Xcode workspace 文件路径, 默认为 `Animeko.xcworkspace`
     * @param scheme 要构建的 scheme 名称, 默认为 `Animeko`
     * @param destination 构建目标平台, 默认为 `generic/platform=iOS` (真机)
     * @param sdk 使用的 SDK, 默认为 `iphoneos`; 模拟器构建时传 `iphonesimulator`
     * @param codeSigningAllowed 是否允许代码签名; `null` 表示不显式设置, 由 Xcode 自行决定
     * @param codeSigningRequired 是否要求代码签名; `null` 表示不显式设置
     * @param additionalBuildSettings 额外的 Xcode build settings 键值对,
     *   例如 `CODE_SIGN_STYLE=Manual`, `DEVELOPMENT_TEAM=XXXXXXXXXX` 等
     * @return 组装好的命令行参数列表, 第一个元素为 `xcodebuild`
     */
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

/**
 * [IpaArguments.create] 的顶层便捷函数, 参数含义完全相同。
 *
 * 在 Gradle build script (`.kts`) 中可以直接调用, 无需通过 `IpaArguments` 对象。
 *
 * @see IpaArguments.create
 */
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

/**
 * Provisioning Profile 解析工具。
 *
 * 用于从 Base64 编码的 `.mobileprovision` 文件内容中提取 UUID 和 Name,
 * 这两个字段在安装 profile 和配置 `exportOptions.plist` 时都需要用到。
 */
object ProvisioningProfileUtils {
    /**
     * 解析后的 Provisioning Profile 信息。
     *
     * @property decoded 原始二进制内容 (解码后的 `.mobileprovision` 文件)
     * @property uuid Profile 的唯一标识符, 用作安装时的文件名 (`<uuid>.mobileprovision`)
     * @property name Profile 的显示名称, 用于 `PROVISIONING_PROFILE_SPECIFIER` build setting
     */
    data class Parsed(
        val decoded: ByteArray,
        val uuid: String,
        val name: String,
    )

    /**
     * 将 Base64 编码的 Provisioning Profile 解码并提取关键信息。
     *
     * 处理步骤:
     * 1. 去除首尾空白和引号 (兼容 CI 环境变量中可能带有的引号)
     * 2. 移除 Base64 字符串中的空白字符, 确保解码成功
     * 3. 使用 MIME 解码器解码 Base64 内容为原始二进制
     * 4. 将二进制内容按 ISO-8859-1 编码转为字符串, 用正则提取 UUID 和 Name
     *
     * @param profileBase64Raw Base64 编码的 `.mobileprovision` 文件内容
     * @return 包含解码后二进制、UUID 和 Name 的 [Parsed] 对象
     * @throws GradleException 如果输入为空、Base64 无效、或无法提取 UUID/Name
     */
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

/**
 * 使用 `xcodebuild archive` 构建 `.xcarchive` 的 Gradle Task。
 *
 * 此 Task 是整个 iOS 构建流水线的核心步骤, 负责将 Xcode workspace 编译并打包为 `.xcarchive` 归档文件。
 * 根据配置的不同, 可以生成未签名的归档 (用于侧载) 或已签名的归档 (用于 App Store 分发)。
 *
 * ## 执行步骤
 *
 * 1. **收集签名相关的 build settings**: 根据 [codeSignStyle]、[developmentTeamId]、[profileName] 等属性,
 *    组装 `CODE_SIGN_STYLE`、`DEVELOPMENT_TEAM`、`PROVISIONING_PROFILE_SPECIFIER` 等 Xcode build settings。
 *
 * 2. **参数校验**: 如果启用了代码签名 ([codeSigningAllowed] = true):
 *    - Automatic 或 Manual 签名模式下, 必须提供 [developmentTeamId]
 *    - Manual 签名模式下, 还必须提供 [profileName] 和签名身份 (`Apple Distribution`)
 *
 * 3. **组装 xcodebuild 命令**: 通过 [IpaArguments.create] 生成基础参数, 再追加
 *    `archive -configuration <config> -archivePath <path>` 参数。
 *
 * 4. **执行 xcodebuild archive**: 在 [workingDirectory] 下运行完整的 xcodebuild 命令,
 *    输出 `.xcarchive` 到 [archivePath]。
 *
 * 5. **清理临时 keychain** (仅当 [signingKeychain] 已设置): 构建完成后尝试删除临时 keychain,
 *    避免在本地开发环境中残留。
 *
 * ## 缓存策略
 *
 * 此 Task 禁用了 Gradle 构建缓存 ([DisableCachingByDefault]),
 * 因为 `xcodebuild` 的输出依赖于本地 Xcode 版本和签名状态, 不适合跨机器缓存。
 *
 * @see IpaArguments 命令行参数构建工具
 * @see BuildUnsignedIpaTask 将 `.xcarchive` 打包为未签名 `.ipa`
 * @see XcodeExportArchiveTask 将 `.xcarchive` 导出为已签名 `.ipa`
 */
@DisableCachingByDefault(because = "Runs xcodebuild, which depends on local Xcode and signing state")
abstract class XcodeArchiveTask : DefaultTask() {
    /** xcodebuild 的工作目录, 通常为 iOS 项目根目录 */
    @get:Internal
    abstract val workingDirectory: DirectoryProperty

    /** Xcode workspace (`.xcworkspace`) 所在目录, 作为增量构建的输入 */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workspaceDirectory: DirectoryProperty

    /** 要构建的 Xcode scheme 名称 */
    @get:Input
    abstract val scheme: Property<String>

    /** 构建目标平台, 例如 `generic/platform=iOS` */
    @get:Input
    abstract val destination: Property<String>

    /** 使用的 SDK, 例如 `iphoneos` 或 `iphonesimulator` */
    @get:Input
    abstract val sdk: Property<String>

    /** 构建配置, 例如 `Debug` 或 `Release` */
    @get:Input
    abstract val archiveConfiguration: Property<String>

    /** 是否允许代码签名 */
    @get:Input
    abstract val codeSigningAllowed: Property<Boolean>

    /** 是否要求代码签名 */
    @get:Input
    abstract val codeSigningRequired: Property<Boolean>

    /** 代码签名方式: `Automatic` (Xcode 自动管理) 或 `Manual` (手动指定 profile) */
    @get:Input
    @get:Optional
    abstract val codeSignStyle: Property<String>

    /** Apple Developer Team ID, 签名构建时必须提供 */
    @get:Input
    @get:Optional
    abstract val developmentTeamId: Property<String>

    /** Provisioning Profile 名称, Manual 签名模式下必须提供 */
    @get:Input
    @get:Optional
    abstract val profileName: Property<String>

    /** 签名身份, 默认为 Apple Distribution */
    @get:Input
    @get:Optional
    abstract val codeSigningIdentity: Property<String>

    /** 临时签名 keychain 的路径, 用于 CI 环境中指定签名证书所在的 keychain */
    @get:InputFile
    @get:Optional
    abstract val signingKeychain: RegularFileProperty

    /** 输出的 `.xcarchive` 目录路径 */
    @get:OutputDirectory
    abstract val archivePath: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun buildArchive() {
        // ── 步骤 1: 收集签名相关的 Xcode build settings ──
        val additionalBuildSettings = mutableMapOf<String, String>()
        // 设置签名方式 (Automatic / Manual)
        codeSignStyle.orNull?.takeIf { it.isNotBlank() }?.let {
            additionalBuildSettings["CODE_SIGN_STYLE"] = it
        }
        // 设置开发者团队 ID
        developmentTeamId.orNull?.takeIf { it.isNotBlank() }?.let {
            additionalBuildSettings["DEVELOPMENT_TEAM"] = it
        }

        // ── 步骤 2: 参数校验 ──
        // 签名构建必须提供 Team ID
        if (codeSigningAllowed.get()
            && (codeSignStyle.orNull == "Automatic" || codeSignStyle.orNull == "Manual")
            && developmentTeamId.orNull.isNullOrBlank()
        ) {
            throw GradleException(
                "Development team ID is required for signed archive builds, " +
                        "specify by parameter \"developmentTeamId\".",
            )
        }
        // Manual 签名模式下, 还需要 Provisioning Profile 名称和签名身份
        if (codeSigningAllowed.get() && codeSignStyle.orNull == "Manual") {
            additionalBuildSettings["PROVISIONING_PROFILE_SPECIFIER"] = profileName.orNull?.takeIf { it.isNotBlank() }
                ?: throw GradleException(
                    "Provisioning profile name is required for manually signed archive builds, " +
                            "specify by parameter \"profileName\".",
                )

            additionalBuildSettings["CODE_SIGN_IDENTITY"] =
                codeSigningIdentity.orNull?.takeIf { it.isNotBlank() } ?: "Apple Distribution"

            // 如果指定了自定义 keychain, 通过 OTHER_CODE_SIGN_FLAGS 传递给 codesign
            signingKeychain.orNull?.let { keychain ->
                additionalBuildSettings["OTHER_CODE_SIGN_FLAGS"] = "--keychain ${keychain.asFile.absolutePath}"
            }
        }

        // ── 步骤 3: 组装 xcodebuild archive 命令 ──
        val args = buildList {
            // 当签名和签名要求不同时启用时, 需要在全局层面显式设置 CODE_SIGNING_ALLOWED/REQUIRED
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
            // 追加 archive 子命令及配置/输出路径
            add("archive")
            add("-configuration")
            add(archiveConfiguration.get())
            add("-archivePath")
            add(archivePath.get().asFile.absolutePath)
        }

        // ── 步骤 4: 执行 xcodebuild archive ──
        execOperations.exec {
            workingDir(workingDirectory.get().asFile)
            commandLine(args)
        }
    }
}

/**
 * 将 `.xcarchive` 中的 `.app` 打包为未签名的 `.ipa` 文件, 并注入 ad-hoc 签名。
 *
 * 此 Task 用于生成可供 AltStore / SideStore 等侧载工具使用的 IPA 文件。
 * 侧载工具会在安装时用用户自己的开发者证书重新签名, 因此这里只需要一个占位的 ad-hoc 签名。
 *
 * ## 执行步骤
 *
 * 1. **定位 .app**: 在 `.xcarchive` 的 `Products/Applications/` 目录下查找 `Animeko.app`。
 *
 * 2. **创建 Payload 目录**: 在 Gradle 临时目录中创建 `Payload/` 文件夹,
 *    并将 `.app` 完整复制进去。IPA 文件规范要求顶层必须有 `Payload/` 目录。
 *
 * 3. **Ad-hoc 签名**: 使用 `codesign --force --deep --sign -` 对 `.app` 进行 ad-hoc 签名。
 *    `--force` 覆盖已有签名, `--deep` 递归签名所有嵌套的 framework 和 bundle,
 *    `--sign -` 表示使用 ad-hoc 身份 (无需证书)。
 *
 * 4. **压缩为 .ipa**: 使用系统 `zip` 命令将 `Payload/` 目录压缩为 `.zip` 文件。
 *    `-r` 递归处理子目录, `-y` 保留符号链接 (iOS framework 中常见)。
 *
 * 5. **重命名输出**: 将 `.zip` 文件移动到最终输出路径并改为 `.ipa` 扩展名。
 *
 * ## 缓存策略
 *
 * 此 Task 启用了 Gradle 构建缓存 ([CacheableTask]),
 * 因为给定相同的 `.xcarchive` 输入, 输出的 `.ipa` 是确定性的。
 *
 * 此 Task 是 configuration-cache 安全的, 不会捕获 `Project` 实例。
 *
 * @see XcodeArchiveTask 生成 `.xcarchive` 的上游 Task
 */
@CacheableTask
abstract class BuildUnsignedIpaTask : DefaultTask() {

    /* -------------------------------------------------------------
     * Inputs / outputs
     * ----------------------------------------------------------- */

    /** 输入的 `.xcarchive` 目录, 由 [XcodeArchiveTask] 生成 */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveDir: DirectoryProperty

    /** 输出的 `.ipa` 文件路径 */
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
        // ── 步骤 1: 定位 .app ──
        // .xcarchive 内部结构为 Products/Applications/<AppName>.app
        // 如果找不到说明上游 XcodeArchiveTask 构建失败或路径配置有误
        val appDir = archiveDir.get().asFile.resolve("Products/Applications/Animeko.app")
        if (!appDir.exists())
            throw GradleException("Could not find Animeko.app in archive at: ${appDir.absolutePath}")

        // ── 步骤 2: 创建 Payload 目录并复制 .app ──
        // IPA 文件本质上是一个 ZIP 压缩包, 其顶层必须包含 Payload/ 目录,
        // Payload/ 目录下放置完整的 .app bundle。
        // 使用 Gradle 的 temporaryDir 作为工作区, 避免污染项目目录。
        val payloadDir = File(temporaryDir, "Payload").apply { mkdirs() }
        val destApp = File(payloadDir, appDir.name)
        appDir.copyRecursively(destApp, overwrite = true)

        // ── 步骤 3: 注入 ad-hoc 签名 ──
        // AltStore / SideStore 等侧载工具要求 .app 至少有一个有效签名才能识别。
        // 使用 ad-hoc 签名 (--sign -) 不需要任何证书, 仅作为占位符。
        // --force: 覆盖 .xcarchive 中可能残留的旧签名
        // --deep: 递归签名所有嵌套的 framework、dylib 和 bundle
        // --timestamp=none: 不请求时间戳服务器, 加快签名速度
        logger.lifecycle("[IPA] Ad‑hoc signing ${destApp.name} …")
        execOperations.exec {
            commandLine(
                "codesign", "--force", "--deep", "--sign", "-", "--timestamp=none",
                destApp.absolutePath,
            )
        }

        // ── 步骤 4: 压缩为 .ipa ──
        // 使用系统 zip 命令将 Payload/ 目录压缩为 ZIP 文件。
        // -r: 递归处理子目录
        // -y: 保留符号链接而非解引用 (iOS framework 中大量使用符号链接)
        // 工作目录设为 temporaryDir, 确保 ZIP 内的路径以 Payload/ 开头。
        val zipFile = File(temporaryDir, "Animeko.zip")
        execOperations.exec {
            workingDir(temporaryDir)
            commandLine("zip", "-r", "-y", zipFile.absolutePath, "Payload")
        }

        // ── 步骤 5: 移动到最终输出路径 ──
        // 将 .zip 重命名为 .ipa (两者格式完全相同, 仅扩展名不同)。
        // 先删除可能存在的旧文件, 再通过 renameTo 原子性地移动。
        outputIpa.get().asFile.apply {
            parentFile.mkdirs()
            delete()
            zipFile.renameTo(this)
        }

        logger.lifecycle("[IPA] Created ad‑hoc‑signed IPA at: ${outputIpa.get().asFile.absolutePath}")
    }
}