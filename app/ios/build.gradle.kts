/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */


// ╔══════════════════════════════════════════════════════════════════════════════╗
// ║  Animeko iOS 构建脚本                                                       ║
// ║                                                                            ║
// ║  本文件定义了 iOS 应用的完整构建流水线, 包括:                                    ║
// ║    - CocoaPods 依赖安装                                                     ║
// ║    - Kotlin/Native iOS framework 编译                                       ║
// ║    - Xcode archive 构建 (Debug / Release / 签名 Release)                    ║
// ║    - IPA 打包 (未签名 ad-hoc / 已签名 App Store)                              ║
// ║    - iOS 模拟器构建、安装和启动                                                ║
// ║    - Info.plist 版本号注入                                                   ║
// ║    - 代码签名相关的 Keychain 和 Provisioning Profile 管理                      ║
// ║                                                                            ║
// ║  构建流水线依赖关系 (签名发布):                                                 ║
// ║    linkPodReleaseFrameworkIosArm64                                          ║
// ║      └─► buildSignedReleaseArchive                                         ║
// ║            ├─► prepareBuildKeychain (创建临时 Keychain)                       ║
// ║            └─► buildSignedReleaseIpa                                        ║
// ║                  ├─► patchExportOptionsPlist                                ║
// ║                  │     └─► installProvisioningProfile                       ║
// ║                  └─► 输出: build/archives/release-signed/export/Animeko.ipa ║
// ╚══════════════════════════════════════════════════════════════════════════════╝

// ── 签名相关的 Gradle 属性 (通常通过 CI 环境变量或 local.properties 提供) ──

/** Apple Developer Team ID (10 位字母数字), 用于 CODE_SIGN_STYLE 和 exportOptions.plist */
val appleDeveloperTeamId = providers.provider {
    project.getPropertyOrNull("APPLE_DEVELOPER_TEAM_ID").orEmpty()
}

/** Base64 编码的 App Store Provisioning Profile (.mobileprovision), 用于签名构建 */
val appStoreProvisioningProfile = providers.provider {
    project.getPropertyOrNull("APPSTORE_PROVISIONING_PROFILE").orEmpty()
}

/** Base64 编码的 Apple Distribution 证书 (.p12/PFX), 包含私钥, 用于创建签名 Keychain */
val appleDistributionPrivateKeyPfx = providers.provider {
    project.getPropertyOrNull("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX").orEmpty()
}

/** .p12 文件的导入密码, 用于解密 PFX 中的私钥 */
val appleDistributionPrivateKeyPfxPassword = providers.provider {
    project.getPropertyOrNull("APPLE_DISTRIBUTION_PRIVATE_KEY_PFX_IMPORT_PWD").orEmpty()
}

/** 从 Provisioning Profile 中提取的显示名称, 用于 PROVISIONING_PROFILE_SPECIFIER */
val appStoreProvisioningProfileName = appStoreProvisioningProfile.map {
    ProvisioningProfileUtils.decode(it).name
}

/** Provisioning Profile 的系统安装目录: ~/Library/MobileDevice/Provisioning Profiles/ */
val provisioningProfilesDirProvider = providers.systemProperty("user.home")
    .map { File(it, "Library/MobileDevice/Provisioning Profiles") }

// ── CocoaPods 依赖安装 ──
// 运行 `pod install` 安装 Podfile 中声明的依赖 (如 Kotlin/Native framework)。
// 依赖于 :app:shared:application:podspec 生成 .podspec 文件,
// 以及 patchInfoPlist 确保 Info.plist 中的版本号已更新。
tasks.register("podInstall", Exec::class) {
    group = "build"
    description = "Builds the iOS framework"
    dependsOn(
        ":app:shared:application:podspec",
        patchInfoPlist,
    )

    workingDir(projectDir)
    commandLine("pod", "install")
}

// ── Kotlin/Native iOS Framework 编译 ──
// 触发 Kotlin Multiplatform 的 iOS framework 编译和嵌入流程。
// 这是 Xcode 构建的前置步骤, 确保 Kotlin 代码已编译为 iOS 可用的 .framework。
tasks.register("iosFramework") {
    group = "build"
    description = "Builds the iOS framework"
    dependsOn(
        ":app:shared:application:embedAndSignPodAppleFrameworkForXcode",
    )
}

// ── 安装 Provisioning Profile ──
// 将 Base64 编码的 .mobileprovision 解码后安装到系统标准目录,
// 使 xcodebuild 在签名构建时能够自动找到对应的 Profile。
// 输入来自 Gradle 属性 APPSTORE_PROVISIONING_PROFILE。
val installProvisioningProfile = tasks.register("installProvisioningProfile", InstallProvisioningProfileTask::class) {
    group = "build"
    description = "Decodes and installs the provisioning profile for App Store distribution"
    provisioningProfileBase64.convention(appStoreProvisioningProfile)
    provisioningProfilesDir = layout.dir(provisioningProfilesDirProvider)
}

// ── 准备签名 Keychain ──
// 创建临时 macOS Keychain 并导入 Apple Distribution 签名证书 (.p12)。
// 这是 CI 环境中进行代码签名的前置步骤, 本地开发通常不需要 (使用系统 Keychain)。
// 输入来自 Gradle 属性 APPLE_DISTRIBUTION_PRIVATE_KEY_PFX 和对应的密码。
val prepareBuildKeychain = tasks.register("prepareBuildKeychain", PrepareBuildKeychainTask::class) {
    group = "build"
    description = "Creates an ephemeral build keychain and imports Apple Distribution signing key material"
    privateKeyPfxBase64.convention(appleDistributionPrivateKeyPfx)
    privateKeyPfxPassword.convention(appleDistributionPrivateKeyPfxPassword)
    outKeychain = layout.buildDirectory.file("release-animeko.keychain")
}

val httpDownloaderProject = project(":utils:http-downloader")
val mediampFfmpegXcframeworkDirectory = httpDownloaderProject.layout.buildDirectory.dir(
    "mediamp-ffmpeg/apple-runtime/MediampFFmpegKit.xcframework",
)
val syncMediampFfmpegAppleRuntimeForXcode = tasks.register<Sync>("syncMediampFfmpegAppleRuntimeForXcode") {
    group = "build"
    description = "Syncs the extracted mediamp FFmpeg XCFramework into the Xcode project"
    dependsOn(":utils:http-downloader:extractMediampFfmpegAppleRuntime")
    from(mediampFfmpegXcframeworkDirectory)
    into(layout.projectDirectory.dir("Frameworks/MediampFFmpegKit.xcframework"))
    includeEmptyDirs = false
}

// ── 生成 exportOptions.plist ──
// 从模板文件生成 xcodebuild -exportArchive 所需的 exportOptions.plist。
// 将模板中的占位符替换为实际的 Team ID 和 Provisioning Profile Name。
// 依赖 installProvisioningProfile 确保 Profile 已安装且 Name 可用。
val patchExportOptionsPlist = tasks.register("patchExportOptionsPlist", PatchExportOptionsPlistTask::class) {
    group = "build"
    dependsOn(installProvisioningProfile)

    templateFile = layout.projectDirectory.file("Animeko/exportOptions.plist.template.txt")
    profileName.convention(appStoreProvisioningProfileName)
    outputFile = layout.buildDirectory.file("export/exportOptions.plist")
    teamId.convention(appleDeveloperTeamId)
}

// ── Debug 未签名 Archive ──
// 使用 xcodebuild archive 构建 Debug 配置的 .xcarchive。
// 禁用代码签名 (codeSigningAllowed=false), 用于本地调试和侧载 IPA 打包。
// 依赖 Kotlin/Native 的 Debug framework 编译。
val buildDebugArchive = tasks.register("buildDebugArchive", XcodeArchiveTask::class) {
    group = "build"
    description = "Builds the iOS framework for Debug"
    dependsOn(
        ":app:shared:application:linkPodDebugFrameworkIosArm64",
        syncMediampFfmpegAppleRuntimeForXcode,
    )

    workingDirectory = layout.projectDirectory
    workspaceDirectory = layout.projectDirectory.dir("Animeko.xcworkspace")
    scheme = "Animeko"
    destination = "generic/platform=iOS"
    sdk = "iphoneos"
    archiveConfiguration = "Debug"
    codeSigningAllowed = false
    codeSigningRequired = false
    archivePath = layout.buildDirectory.dir("archives/debug/Animeko.xcarchive")
}

// ── Release 未签名 Archive ──
// 使用 xcodebuild archive 构建 Release 配置的 .xcarchive。
// 禁用代码签名, 用于生成可供 AltStore/SideStore 侧载的未签名 IPA。
// 依赖 Kotlin/Native 的 Release framework 编译。
val buildReleaseArchive = tasks.register("buildReleaseArchive", XcodeArchiveTask::class) {
    group = "build"
    description = "Builds the iOS framework for Release"
    dependsOn(
        ":app:shared:application:linkPodReleaseFrameworkIosArm64",
        syncMediampFfmpegAppleRuntimeForXcode,
    )

    workingDirectory = layout.projectDirectory
    workspaceDirectory = layout.projectDirectory.dir("Animeko.xcworkspace")
    scheme = "Animeko"
    destination = "generic/platform=iOS"
    sdk = "iphoneos"
    archiveConfiguration = "Release"
    codeSigningAllowed = false
    codeSigningRequired = false
    archivePath = layout.buildDirectory.dir("archives/release/Animeko.xcarchive")
}

// ── 签名 Release Archive (App Store 分发) ──
// 使用 xcodebuild archive 构建 Release 配置的已签名 .xcarchive。
// 启用 Manual 代码签名, 使用 prepareBuildKeychain 创建的临时 Keychain 中的证书。
// 这是 App Store / TestFlight 分发流水线的核心步骤。
// 依赖: Kotlin/Native Release framework + 临时签名 Keychain。
val buildSignedReleaseArchive = tasks.register("buildSignedReleaseArchive", XcodeArchiveTask::class) {
    group = "build"
    description = "Builds a signed iOS archive for App Store distribution"
    dependsOn(
        ":app:shared:application:linkPodReleaseFrameworkIosArm64",
        prepareBuildKeychain,
        syncMediampFfmpegAppleRuntimeForXcode,
    )

    workingDirectory = layout.projectDirectory
    workspaceDirectory = layout.projectDirectory.dir("Animeko.xcworkspace")
    scheme = "Animeko"
    destination = "generic/platform=iOS"
    sdk = "iphoneos"
    archiveConfiguration = "Release"
    codeSigningAllowed = true
    codeSigningRequired = true
    codeSignStyle = "Manual"
    profileName.convention(appStoreProvisioningProfileName)
    developmentTeamId.convention(appleDeveloperTeamId)
    signingKeychain = layout.buildDirectory.file("release-animeko.keychain")
    archivePath = layout.buildDirectory.dir("archives/release-signed/Animeko.xcarchive")
}

// ── Debug 未签名 IPA ──
// 将 Debug .xcarchive 中的 .app 打包为 ad-hoc 签名的 .ipa。
// 适用于 AltStore / SideStore 等侧载工具, 它们会在安装时重新签名。
tasks.register("buildDebugIpa", BuildUnsignedIpaTask::class) {
    description = "Manually packages the .app from the .xcarchive into an unsigned .ipa"
    group = "build"

    // Adjust these paths as needed
    archiveDir = layout.buildDirectory.dir("archives/debug/Animeko.xcarchive")
    outputIpa = layout.buildDirectory.file("archives/debug/Animeko.ipa")
    dependsOn(buildDebugArchive)
}

// ── Release 未签名 IPA ──
// 将 Release .xcarchive 中的 .app 打包为 ad-hoc 签名的 .ipa。
// 与 Debug 版本类似, 但使用 Release 优化编译, 适用于侧载分发。
tasks.register("buildReleaseIpa", BuildUnsignedIpaTask::class) {
    description = "Manually packages the .app from the .xcarchive into an unsigned .ipa"
    group = "build"

    // Adjust these paths as needed
    archiveDir = layout.buildDirectory.dir("archives/release/Animeko.xcarchive")
    outputIpa = layout.buildDirectory.file("archives/release/Animeko.ipa")
    dependsOn(buildReleaseArchive)
}

// ── 签名 Release IPA (App Store / TestFlight) ──
// 使用 xcodebuild -exportArchive 将已签名的 .xcarchive 导出为正式的 .ipa。
// 这是整个签名发布流水线的最终产物, 可直接上传到 App Store Connect / TestFlight。
// 依赖: 已签名的 .xcarchive + 生成的 exportOptions.plist。
tasks.register("buildSignedReleaseIpa", XcodeExportArchiveTask::class) {
    group = "build"
    description = "Exports a signed IPA for App Store / TestFlight distribution"
    dependsOn(buildSignedReleaseArchive, patchExportOptionsPlist)

    workingDirectory = layout.projectDirectory
    archivePath = layout.buildDirectory.dir("archives/release-signed/Animeko.xcarchive")
    exportPath = layout.buildDirectory.dir("archives/release-signed/export")
    exportOptionsPlist = layout.buildDirectory.file("export/exportOptions.plist")
    outputIpa = layout.buildDirectory.file("archives/release-signed/export/Animeko.ipa")
}


/// ── 以下为本地调试用 Task ──

/**
 * 获取模拟器构建产物 (.app) 的路径。
 * Xcode 默认将模拟器构建输出到 DerivedData, 但此处使用自定义的 build 目录。
 */
fun simulatorAppPath(): Provider<RegularFile> =
    layout.buildDirectory.file("derivedData/Build/Products/Debug-iphonesimulator/Animeko.app")

fun simulatorAppDir(): Provider<Directory> =
    layout.buildDirectory.dir("derivedData/Build/Products/Debug-iphonesimulator/Animeko.app")

// ── 模拟器 Debug 构建 ──
// 使用 xcodebuild build (非 archive) 为 iOS 模拟器编译 Debug 版本。
// 目标平台通过 local.properties 中的 ani.ios.simulator.destination 配置,
// 默认为 generic/platform=iOS Simulator (通用模拟器)。
// 注意: 模拟器使用 iphonesimulator SDK, 与真机的 iphoneos SDK 不同。
tasks.register("buildDebugForSimulator", Exec::class) {
    group = "build"
    description = "Builds a debug version of Animeko for iOS Simulator"
    dependsOn(syncMediampFfmpegAppleRuntimeForXcode)
    val destination = project.getLocalProperty("ani.ios.simulator.destination") ?: "generic/platform=iOS Simulator"
    val simulatorArchs = project.getLocalProperty("ani.ios.simulator.archs") ?: "arm64"
    inputs.property("destination", destination)
    inputs.property("simulatorArchs", simulatorArchs)
    workingDir(projectDir)
    environment("ORG_GRADLE_PROJECT_compose.ios.resources.platform", "iphonesimulator")
    environment("ORG_GRADLE_PROJECT_compose.ios.resources.archs", simulatorArchs)
    val command = buildList {
        addAll(
            ipaArguments(
                destination = destination,
                sdk = "iphonesimulator",
            ),
        )
        add("-derivedDataPath")
        add(layout.buildDirectory.dir("derivedData").get().asFile.absolutePath)
        add("-configuration")
        add("Debug")
        add("ARCHS=$simulatorArchs")
        add("build")
    }
    commandLine(command)
}

// ── 启动 iOS 模拟器 ──
// 通过 macOS 的 open 命令启动 Simulator.app。
// 如需指定特定设备, 可改用 `xcrun simctl boot "iPhone 15"` 等命令。
tasks.register("launchSimulator", Exec::class) {
    group = "run"
    description = "Launches the iOS simulator"

    // This opens the default iOS Simulator app on macOS. 
    // You can also explicitly boot a specific device via `xcrun simctl boot "iPhone 14"` 
    // if you want more control.
    commandLine("open", "-a", "Simulator")
}

// ── 安装 Debug 构建到模拟器 ──
// 先构建 Debug 版本并启动模拟器, 然后使用 xcrun simctl install 将 .app 安装到已启动的模拟器。
// "booted" 参数表示安装到当前已启动的模拟器设备。
tasks.register("installDebugOnSimulator", Exec::class) {
    group = "run"
    description = "Installs the debug build on the Simulator"
    dependsOn("buildDebugForSimulator", "launchSimulator")

    val appPath = simulatorAppPath()
    // Typically, you want to ensure the simulator is booted before installing.
    commandLine("xcrun", "simctl", "install", "booted", appPath.get().asFile.absolutePath)
}

// ── 在模拟器上启动应用 ──
// 使用 xcrun simctl launch 通过 Bundle ID 启动已安装的应用。
// 依赖 installDebugOnSimulator 确保应用已安装。
tasks.register("launchAppOnSimulator", Exec::class) {
    group = "run"
    description = "Launches the Animeko app on the simulator"
    dependsOn("installDebugOnSimulator")

    commandLine("xcrun", "simctl", "launch", "booted", "org.animeko.animeko")
}

// ── 注入版本号到 Info.plist ──
// 从 Gradle 属性 version.name 和 android.version.code 读取版本信息,
// 使用正则替换模板文件中的 CFBundleShortVersionString 和 CFBundleVersion,
// 确保 iOS 应用的版本号与 Android 端保持一致。
val patchInfoPlist = tasks.register("patchInfoPlist", Task::class) {
    group = "run"
    description = "Patches Info.plist"

    val versionName = getProperty("version.name").substringBefore("-")
    inputs.property("version.name", versionName)
    val versionCode = getProperty("ios.version.code")
    inputs.property("version.code", versionCode)

    val templateFile = file("Animeko/Info.plist.template.txt")
    val outputFile = file("Animeko/Info.plist")
    inputs.file(templateFile)
    outputs.file(outputFile)

    doLast {
        val text = templateFile.readText()

        // Replace CFBundleShortVersionString with versionName
        // and CFBundleVersion with versionCode using a simple regex
        val updated = text
            .replace(
                Regex("""(<key>CFBundleShortVersionString</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$versionName$3",
            )
            .replace(
                Regex("""(<key>CFBundleVersion</key>\s*<string>)([^<]+)(</string>)"""),
                "$1$versionCode$3",
            )

        if (updated != text) {
            outputFile.writeText(updated)
            logger.lifecycle("Patched Info.plist with versionName=$versionName and versionCode=$versionCode")
        } else {
            logger.lifecycle("No changes needed or did not match expected patterns in Info.plist.")
        }
    }
}

// ── 确保 Xcode 构建前 Info.plist 已更新 ──
// 拦截 Kotlin/Native 的 embedAndSignPodAppleFrameworkForXcode task,
// 添加对 patchInfoPlist 的依赖, 确保版本号在 framework 嵌入前已注入。
tasks.matching { it.path == ":app:shared:application:embedAndSignPodAppleFrameworkForXcode" }.configureEach {
    dependsOn(patchInfoPlist)
    inputs.file(file("Animeko/Info.plist"))
}
