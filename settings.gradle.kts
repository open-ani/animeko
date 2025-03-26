/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import java.util.Properties

/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

rootProject.name = "animeko"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // Compose Multiplatform pre-release versions
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("anitorrentLibs") {
            from("org.openani.anitorrent:catalog:0.1.1")
        }

        create("mediampLibs") {
            from("org.openani.mediamp:catalog:0.0.22")
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

fun includeProject(projectPath: String, dir: String? = null) {
    include(projectPath)
    if (dir != null) project(projectPath).projectDir = file(dir)
}

// Utilities shared by client and server (targeting JVM)
includeProject(":utils:platform") // 适配各个平台的基础 API
includeProject(":utils:intellij-annotations")
includeProject(":utils:logging") // shared by client and server (targets JVM)
includeProject(":utils:serialization", "utils/serialization")
includeProject(":utils:coroutines", "utils/coroutines")
includeProject(":utils:ktor-client", "utils/ktor-client")
includeProject(":utils:io", "utils/io")
includeProject(":utils:testing", "utils/testing")
includeProject(":utils:xml")
includeProject(":utils:jsonpath")
includeProject(":utils:bbcode", "utils/bbcode")
includeProject(":utils:bbcode:test-codegen")
includeProject(":utils:ip-parser", "utils/ip-parser")
includeProject(":utils:ui-testing")
includeProject(":utils:androidx-lifecycle-runtime-testing")
includeProject(":utils:ui-preview")
includeProject(":utils:analytics")
includeProject(":utils:http-downloader")


includeProject(":torrent:torrent-api", "torrent/api") // Torrent 系统 API
includeProject(":torrent:anitorrent")
//includeProject(":torrent:anitorrent:anitorrent-native")

includeProject(":app:shared")
includeProject(":app:shared:app-platform")
includeProject(":app:shared:app-data")
includeProject(":app:shared:ui-foundation")
includeProject(":app:shared:ui-settings")
includeProject(":app:shared:ui-adaptive")
includeProject(":app:shared:ui-subject")
includeProject(":app:shared:ui-exploration")
includeProject(":app:shared:ui-comment")
includeProject(":app:shared:ui-onboarding")
includeProject(":app:shared:ui-mediaselect")
includeProject(":app:shared:ui-episode")
includeProject(":app:shared:video-player:video-player-api", "app/shared/video-player/api")
includeProject(":app:shared:video-player:torrent-source")
includeProject(":app:shared:video-player")
includeProject(":app:shared:application")

includeProject(":app:shared:placeholder", "app/shared/thirdparty/placeholder")
includeProject(":app:shared:paging-compose", "app/shared/thirdparty/paging-compose")
includeProject(":app:shared:image-viewer", "app/shared/thirdparty/image-viewer")
includeProject(":app:shared:reorderable", "app/shared/thirdparty/reorderable")

includeProject(":app:desktop", "app/desktop") // desktop JVM client for macOS, Windows, and Linux
includeProject(":app:android", "app/android") // Android client

includeProject(":client")

// server
//includeProject(":server:core", "server/core") // server core
//includeProject(":server:database", "server/database") // server database interfaces
//includeProject(":server:database-xodus", "server/database-xodus") // database implementation with Xodus

// data sources
includeProject(":datasource:datasource-api", "datasource/api") // data source interfaces: Media, MediaSource 
includeProject(":datasource:datasource-api:test-codegen", "datasource/api/test-codegen") // 生成单元测试
includeProject(
    ":datasource:datasource-core",
    "datasource/core",
) // data source managers: MediaFetcher, MediaCacheStorage
includeProject(":datasource:bangumi", "datasource/bangumi") // https://bangumi.tv
//   BT 数据源
includeProject(":datasource:dmhy", "datasource/bt/dmhy") // https://dmhy.org
includeProject(":datasource:mikan", "datasource/bt/mikan") // https://mikanani.me/
//   Web 数据源
includeProject(":datasource:web-base", "datasource/web/web-base") // web 基础
includeProject(":datasource:jellyfin", "datasource/jellyfin")
includeProject(":datasource:ikaros", "datasource/ikaros") // https://ikaros.run/

// danmaku
includeProject(":danmaku:danmaku-ui-config", "danmaku/ui-config")
includeProject(":danmaku:danmaku-api", "danmaku/api")
includeProject(":danmaku:danmaku-ui", "danmaku/ui")
includeProject(":danmaku:dandanplay", "danmaku/dandanplay")

includeProject(
    ":datasource:dmhy:dataset-tools",
    "datasource/bt/dmhy/dataset-tools",
) // tools for generating dataset for ML title parsing

// ci
includeProject(":ci-helper", "ci-helper") // 

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


val localPropertiesFile: File get() = rootProject.projectDir.resolve("local.properties")
fun findLocalProperty(key: String): String? {
    return if (localPropertiesFile.exists()) {
        val properties = Properties()
        localPropertiesFile.inputStream().buffered().use { input ->
            properties.load(input)
        }
        properties.getProperty(key)
    } else {
        localPropertiesFile.createNewFile()
        null
    }
}

findLocalProperty("ani.build.mediamp.path")?.let { mediampPath ->
    println("i:: Including mediamp as a Composite Build from: $mediampPath")
    includeBuild(mediampPath) {
        dependencySubstitution {
            substitute(module("org.openani.mediamp:mediamp-api"))
                .using(project(":mediamp-api"))
            substitute(module("org.openani.mediamp:mediamp-compose"))
                .using(project(":mediamp-compose"))
            substitute(module("org.openani.mediamp:mediamp-exoplayer"))
                .using(project(":mediamp-exoplayer"))
            substitute(module("org.openani.mediamp:mediamp-exoplayer-compose"))
                .using(project(":mediamp-exoplayer-compose"))
            substitute(module("org.openani.mediamp:mediamp-vlc"))
                .using(project(":mediamp-vlc"))
            substitute(module("org.openani.mediamp:mediamp-vlc-compose"))
                .using(project(":mediamp-vlc-compose"))
            substitute(module("org.openani.mediamp:mediamp-source-ktxio"))
                .using(project(":mediamp-source-ktxio"))
        }
    }
}

findLocalProperty("ani.build.anitorrent.path")?.let { anitorrentPath ->
    println("i:: Including anitorrent as a Composite Build from: $anitorrentPath")
    includeBuild(anitorrentPath) {
        dependencySubstitution {
            substitute(module("org.openani.anitorrent:anitorrent-native"))
                .using(project(":anitorrent-native"))
            substitute(module("org.openani.anitorrent:anitorrent-native-desktop-jni"))
                .using(project(":anitorrent-native-desktop-jni"))
        }
    }
}