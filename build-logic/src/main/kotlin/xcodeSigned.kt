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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
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
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

/**
 * 创建临时 macOS Keychain 并导入 Apple Distribution 签名证书的 Gradle Task。
 *
 * 在 CI/CD 环境中, 签名证书通常以 Base64 编码的 `.p12` (PFX) 格式存储在环境变量中,
 * 而非预装在系统 Keychain 里。此 Task 负责将这些密钥材料安全地导入到一个临时 Keychain 中,
 * 供后续的 `xcodebuild archive` 和 `codesign` 使用。
 *
 * ## 执行步骤
 *
 * 1. **解码 PFX 密钥**: 从 [privateKeyPfxBase64] 读取 Base64 编码的 `.p12` 文件内容,
 *    解码后写入临时文件 `apple_distribution_key.p12`。
 *
 * 2. **清理旧 Keychain**: 尝试删除同名的已有 Keychain (本地开发时可能残留),
 *    CI 上通常不存在, 所以忽略错误。
 *
 * 3. **创建新 Keychain**: 使用随机生成的 UUID 作为密码创建一个新的 Keychain 文件。
 *    随机密码确保每次构建的 Keychain 密码不同, 增强安全性。
 *
 * 4. **配置 Keychain 超时**: 设置 Keychain 在 6 小时 (21600 秒) 后自动上锁.
 *
 * 5. **导入签名证书**: 将 `.p12` 文件导入 Keychain, 并通过 `-T` 参数授权
 *    `codesign`、`security`、`xcodebuild`、`productbuild` 四个工具访问密钥。
 *
 * 6. **设置分区列表 (Partition List)**: 调用 `security set-key-partition-list` 配置
 *    ACL (Access Control List), 允许 `apple-tool:`、`apple:` 和 `codesign:` 分区
 *    无需弹窗确认即可访问密钥。这是 macOS Sierra+ 的安全要求。
 *
 * 7. **导入 Apple 中间证书**: 从 Apple 官方下载并导入三个中间 CA 证书:
 *    - AppleWWDRCAG3.cer (Worldwide Developer Relations G3)
 *    - AppleWWDRCAG4.cer (Worldwide Developer Relations G4)
 *    - DeveloperIDG2CA.cer (Developer ID G2)
 *    
 *    这些中间证书构成了从 Apple Root CA 到开发者证书的完整信任链。
 *
 * 8. **注册到系统搜索路径**: 将新 Keychain 追加到用户级 Keychain 搜索列表中,
 *    使 `codesign` 和 `xcodebuild` 能够自动发现其中的证书。
 *    同时保留已有的 Keychain (如 login.keychain), 避免破坏用户环境。
 *
 * 9. **解锁 Keychain**: 使用创建时的随机密码立即解锁 Keychain,
 *    确保后续构建步骤可以无交互地访问签名密钥。
 *
 * 10. **验证签名身份**: 在 Keychain 中搜索 `Apple Distribution` 或 `iOS Distribution`
 *     签名身份, 如果找不到则抛出异常, 提示用户检查 PFX 文件内容。
 *
 * ## 缓存策略
 *
 * 此 Task 禁用了 Gradle 构建缓存 ([DisableCachingByDefault]),
 * 因为它操作的是本地 macOS Keychain 状态, 不适合跨机器或跨构建缓存。
 *
 * @see XcodeArchiveTask 使用此 Keychain 进行签名构建
 * @see InstallProvisioningProfileTask 安装配套的 Provisioning Profile
 */
@DisableCachingByDefault(because = "Creates and unlocks an ephemeral macOS keychain for codesigning")
abstract class PrepareBuildKeychainTask : DefaultTask() {
    /** Base64 编码的 `.p12` (PFX) 文件内容, 包含 Apple Distribution 证书和私钥 */
    @get:Input
    @get:Optional
    abstract val privateKeyPfxBase64: Property<String>

    /** `.p12` 文件的导入密码, 用于解密 PFX 中的私钥 */
    @get:Input
    @get:Optional
    abstract val privateKeyPfxPassword: Property<String>

    /** 输出的 Keychain 文件路径 */
    @get:OutputFile
    abstract val outKeychain: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun prepare() {
        // ── 步骤 1: 解码 PFX 密钥文件 ──
        // 从环境变量/Gradle 属性中读取 Base64 编码的 .p12 内容,
        // 去除可能存在的引号包裹 (CI 环境变量有时会自动添加引号),
        // 然后解码为二进制并写入临时文件。
        val encodedPfx = privateKeyPfxBase64.orNull?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
            ?: throw GradleException("privateKeyPfxBase64 is null or blank, please check yor parameters where creating the task.")
        val importPassword = privateKeyPfxPassword.orNull?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() }
            ?: throw GradleException("privateKeyPfxPassword is null or blank, please check yor parameters where creating the task.")
        // 使用随机 UUID 作为 Keychain 密码, 每次构建都不同, 增强安全性
        val keychainPassword = UUID.randomUUID().toString()

        val outKeychainFilePath = outKeychain.asFile.get().absolutePath
        val tempPfxKeyFile = temporaryDir.resolve("${UUID.randomUUID()}.p12").apply {
            val decoded = try {
                Base64.getMimeDecoder().decode(encodedPfx.replace(Regex("\\s+"), ""))
            } catch (e: IllegalArgumentException) {
                throw GradleException("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX is not valid base64 content.", e)
            }
            writeBytes(decoded)
        }

        // ── 步骤 2: 清理可能残留的旧 Keychain ──
        // 在 CI 上通常不存在同名 Keychain, 但本地开发反复测试时可能残留,
        // 删除失败不影响后续流程, 所以用 || true 忽略错误。
        execOperations.exec {
            commandLine("sh", "-c", "security delete-keychain \"$outKeychainFilePath\" 2>/dev/null || true")
        }

        // ── 步骤 3: 创建新的临时 Keychain ──
        // 使用随机密码创建 Keychain, 后续步骤会将签名证书导入其中。
        execOperations.exec { commandLine("security", "create-keychain", "-p", keychainPassword, outKeychainFilePath) }

        // ── 步骤 4: 配置 Keychain 超时策略 ──
        // -l: 锁定时需要密码
        // -u: 解锁后经过指定秒数自动上锁
        // -t 21600: 超时时间为 6 小时 (足够完成一次完整构建)
        execOperations.exec {
            commandLine("security", "set-keychain-settings", "-lut", "21600", "-u", outKeychainFilePath)
        }

        // ── 步骤 5: 导入 Apple Distribution 签名证书 ──
        // -k: 指定目标 Keychain
        // -P: .p12 文件的导入密码
        // -T: 授权指定程序访问导入的密钥 (无需弹窗确认)
        //     授权 codesign、security、xcodebuild、productbuild 四个工具
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

        // 导入完成后删除临时存放的 key
        tempPfxKeyFile.deleteRecursively()

        // ── 步骤 6: 设置密钥分区列表 (Partition List) ──
        // macOS Sierra+ 引入了分区列表机制, 即使 -T 授权了程序,
        // 还需要通过 set-key-partition-list 配置 ACL 分区才能真正无交互访问。
        // -S: 指定允许的分区 (apple-tool:, apple:, codesign:)
        // -s: 对所有密钥生效
        // -k: 提供 Keychain 密码以授权修改
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

        // ── 步骤 7: 下载并导入 Apple 中间 CA 证书 ──
        // 代码签名验证需要完整的证书信任链:
        //   Apple Root CA → 中间 CA → 开发者证书
        // 系统 Keychain 中通常已有 Root CA, 但中间证书可能缺失 (尤其在 CI 环境),
        // 因此从 Apple 官方下载三个中间证书并导入:
        //   - AppleWWDRCAG3: 用于 App Store 和 TestFlight 分发
        //   - AppleWWDRCAG4: G3 的后继版本, 覆盖更新的证书
        //   - DeveloperIDG2CA: 用于 Developer ID 签名 (macOS 分发)
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


        // ── 步骤 8: 将新 Keychain 注册到用户级搜索路径 ──
        // macOS 的 security 框架通过搜索路径查找 Keychain,
        // 如果不把新 Keychain 加入搜索路径, codesign 和 xcodebuild 将无法发现其中的证书。
        // 先获取当前的用户级 Keychain 列表, 过滤掉可能已存在的同名条目,
        // 然后将新 Keychain 追加到列表末尾, 保留 login.keychain 等已有 Keychain。
        kotlin.run {
            val keychainsOutput = ByteArrayOutputStream()
            execOperations.exec {
                commandLine("security", "list-keychains", "-d", "user")
                standardOutput = keychainsOutput
            }
            val existingUserKeys = keychainsOutput.toString().split('\n').map { it.trim('"', ' ') }
                // 过滤掉可能已存在的同名 Keychain, 避免重复注册
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

        // ── 步骤 9: 解锁 Keychain ──
        // 新创建的 Keychain 默认处于锁定状态, 必须用密码解锁后才能访问其中的密钥。
        // 解锁状态会持续到步骤 4 中设置的超时时间 (6 小时) 到期。
        execOperations.exec { commandLine("security", "unlock-keychain", "-p", keychainPassword, outKeychainFilePath) }

        // ── 步骤 10: 验证签名身份是否成功导入 ──
        // 在 Keychain 中搜索 codesigning 用途的身份 (identity),
        // 确认至少存在一个 "Apple Distribution" 或 "iOS Distribution" 身份。
        // 如果找不到, 说明 PFX 文件可能只包含私钥或 CSR, 而非完整的证书+私钥 bundle。
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

/**
 * 解码并安装 Provisioning Profile 到系统标准目录的 Gradle Task。
 *
 * Xcode 在构建签名应用时, 会从 `~/Library/MobileDevice/Provisioning Profiles/` 目录
 * 查找可用的 Provisioning Profile。此 Task 将 Base64 编码的 `.mobileprovision` 文件
 * 解码后安装到该目录, 使 `xcodebuild` 能够在签名构建时自动找到它。
 *
 * ## 执行步骤
 *
 * 1. **读取 Base64 内容**: 从 [provisioningProfileBase64] 获取编码后的 profile 内容,
 *    该值通常来自 CI 环境变量 `APPSTORE_PROVISIONING_PROFILE`。
 *
 * 2. **解码并提取元数据**: 调用 [ProvisioningProfileUtils.decode] 解码 Base64 内容,
 *    提取 UUID 和 Name。UUID 用作文件名, Name 用于后续的 build settings 配置。
 *
 * 3. **写入文件**: 将解码后的二进制内容写入
 *    `~/Library/MobileDevice/Provisioning Profiles/<UUID>.mobileprovision`。
 *    Xcode 要求文件名必须是 `<UUID>.mobileprovision` 格式才能正确识别。
 *
 * @see ProvisioningProfileUtils 用于解码 Base64 编码的 profile
 * @see PatchExportOptionsPlistTask 使用 profile 的 Name 生成 exportOptions.plist
 */
@DisableCachingByDefault(because = "Consumes signing secrets and writes provisioning profiles to the user home directory")
abstract class InstallProvisioningProfileTask : DefaultTask() {
    /** Base64 编码的 `.mobileprovision` 文件内容 */
    @get:Input
    @get:Optional
    abstract val provisioningProfileBase64: Property<String>

    /** Provisioning Profile 的安装目录, 默认为 `~/Library/MobileDevice/Provisioning Profiles/` */
    @get:OutputDirectory
    abstract val provisioningProfilesDir: DirectoryProperty

    @TaskAction
    fun install() {
        // 步骤 1: 验证输入
        val profileBase64 = provisioningProfileBase64.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPSTORE_PROVISIONING_PROFILE is not set.")
        val profilesDir = provisioningProfilesDir.get().asFile.apply { mkdirs() }

        // 步骤 2: 解码 Base64 并提取 UUID 和 Name
        val profile = ProvisioningProfileUtils.decode(profileBase64)

        // 步骤 3: 以 <UUID>.mobileprovision 为文件名写入安装目录
        val profileFile = profilesDir.resolve("${profile.uuid}.mobileprovision")
        profileFile.writeBytes(profile.decoded)
        logger.lifecycle("Installed provisioning profile: ${profileFile.absolutePath} (UUID=${profile.uuid}, Name=${profile.name})")
    }
}

/**
 * 根据模板生成 `exportOptions.plist` 的 Gradle Task。
 *
 * `exportOptions.plist` 是 `xcodebuild -exportArchive` 命令的必需配置文件,
 * 定义了 IPA 导出时的签名方式、Team ID、Provisioning Profile 映射等关键参数。
 *
 * 由于 Team ID 和 Profile Name 属于敏感信息且因环境而异, 不适合硬编码在版本控制中,
 * 因此采用模板替换的方式: 在模板文件中使用占位符, 构建时动态替换为实际值。
 *
 * ## 执行步骤
 *
 * 1. **验证输入**: 确认 [teamId] 和 [profileName] 均已提供且非空。
 *
 * 2. **读取模板**: 从 [templateFile] 读取 `exportOptions.plist` 模板内容。
 *
 * 3. **替换 Team ID**: 使用正则表达式匹配 `<key>teamID</key>` 后的 `<string>` 值,
 *    替换为实际的 Apple Developer Team ID。
 *
 * 4. **替换 Profile 映射**: 匹配 `<key>org.animeko.animeko</key>` (应用 Bundle ID)
 *    后的 `<string>` 值, 替换为实际的 Provisioning Profile 名称。
 *
 * 5. **写入输出文件**: 将替换后的内容写入 [outputFile], 供 [XcodeExportArchiveTask] 使用。
 *
 * @see XcodeExportArchiveTask 使用生成的 exportOptions.plist 导出 IPA
 * @see InstallProvisioningProfileTask 安装 Profile 并提取 Name
 */
@DisableCachingByDefault(because = "Generates exportOptions.plist from template and signing secrets")
abstract class PatchExportOptionsPlistTask : DefaultTask() {
    /** exportOptions.plist 模板文件, 包含待替换的占位符 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val templateFile: RegularFileProperty

    /** Provisioning Profile 的显示名称, 用于 Bundle ID 到 Profile 的映射 */
    @get:Input
    @get:Optional
    abstract val profileName: Property<String>

    /** Apple Developer Team ID (10 位字母数字字符串) */
    @get:Input
    @get:Optional
    abstract val teamId: Property<String>

    /** 生成的 exportOptions.plist 输出路径 */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun patch() {
        // 步骤 1: 验证必需参数
        val resolvedTeamId = teamId.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("APPLE_DEVELOPER_TEAM_ID is not set")
        val resolvedProfileName = profileName.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException("Could not resolve provisioning profile Name from APPSTORE_PROVISIONING_PROFILE")

        // 步骤 2: 读取模板文件
        val text = templateFile.get().asFile.readText()

        // 步骤 3-4: 替换 teamID 和 Bundle ID 对应的 Profile Name
        val updated = text
            .replace(
                Regex("""(<key>teamID</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$resolvedTeamId$3",
            )
            .replace(
                Regex("""(<key>org\.animeko\.animeko</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$resolvedProfileName$3",
            )

        // 步骤 5: 写入输出文件
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(updated)
        }
        logger.lifecycle("Generated exportOptions.plist with teamId=$resolvedTeamId, profileName=$resolvedProfileName")
    }
}

/**
 * 使用 `xcodebuild -exportArchive` 将已签名的 `.xcarchive` 导出为 `.ipa` 的 Gradle Task。
 *
 * 这是 iOS App Store / TestFlight 分发流水线的最后一步。
 * 与 [BuildUnsignedIpaTask] 手动打包不同, 此 Task 使用 Xcode 官方的导出流程,
 * 会根据 `exportOptions.plist` 中的配置进行正式的代码签名和 App Thinning。
 *
 * ## 执行步骤
 *
 * 1. **执行 xcodebuild -exportArchive**: 调用 Xcode 命令行工具, 传入:
 *    - `-archivePath`: 输入的 `.xcarchive` 路径 (由 [XcodeArchiveTask] 生成)
 *    - `-exportPath`: IPA 和导出日志的输出目录
 *    - `-exportOptionsPlist`: 导出配置文件 (由 [PatchExportOptionsPlistTask] 生成)
 *
 * 2. **验证输出**: 检查预期路径下是否生成了 `.ipa` 文件。
 *    如果文件不存在, 说明导出过程中出现了错误 (如签名身份不匹配、Profile 过期等)。
 *
 * @see XcodeArchiveTask 生成 `.xcarchive` 的上游 Task
 * @see PatchExportOptionsPlistTask 生成 `exportOptions.plist` 的上游 Task
 * @see PrepareBuildKeychainTask 准备签名 Keychain 的上游 Task
 */
@DisableCachingByDefault(because = "Runs xcodebuild exportArchive against local Apple toolchain")
abstract class XcodeExportArchiveTask : DefaultTask() {
    /** xcodebuild 的工作目录, 通常为 iOS 项目根目录 */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workingDirectory: DirectoryProperty

    /** 输入的 `.xcarchive` 目录路径, 由 [XcodeArchiveTask] 生成 */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archivePath: DirectoryProperty

    /** `exportOptions.plist` 文件路径, 由 [PatchExportOptionsPlistTask] 生成 */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val exportOptionsPlist: RegularFileProperty

    /** IPA 和导出日志的输出目录 */
    @get:OutputDirectory
    abstract val exportPath: DirectoryProperty

    /** 最终输出的 `.ipa` 文件路径 */
    @get:OutputFile
    abstract val outputIpa: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun exportIpa() {
        // 步骤 1: 执行 xcodebuild -exportArchive
        // 此命令会读取 .xcarchive 中的已签名 .app, 根据 exportOptions.plist 的配置
        // 进行 App Thinning、重新签名 (如需要) 并打包为 .ipa。
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

        // 步骤 2: 验证 IPA 文件是否成功生成
        val ipaFile = outputIpa.get().asFile
        if (!ipaFile.exists()) {
            throw GradleException("Signed IPA was not produced at expected path: ${ipaFile.absolutePath}")
        }
    }
}