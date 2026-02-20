/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

/**
 * 扁平化源集目录结构, 减少文件树层级 by 2
 *
 * 变化:
 * ```
 * src/${targetName}Main/kotlin -> ${targetName}Main
 * src/${targetName}Main/resources -> ${targetName}Resources
 * src/${targetName}Test/kotlin -> ${targetName}Test
 * src/${targetName}Test/resources -> ${targetName}TestResources
 * ```
 *
 * `${targetName}` 可以是 `common`, `android` `desktop` 等.
 */
fun configureFlattenSourceSets() {
    val flatten = extra.runCatching { get("flatten.sourceset") }.getOrNull()?.toString()?.toBoolean() ?: true
    if (!flatten) return
    sourceSets {
        findByName("main")?.apply {
            resources.srcDirs(listOf(projectDir.resolve("resources")))
            java.srcDirs(listOf(projectDir.resolve("src")))
        }
        findByName("test")?.apply {
            resources.srcDirs(listOf(projectDir.resolve("testResources")))
            java.srcDirs(listOf(projectDir.resolve("test")))
        }
    }
}

/**
 * 扁平化多平台项目的源集目录结构, 减少文件树层级 by 2
 *
 * 变化:
 * ```
 * src/androidMain/res -> androidRes
 * src/androidMain/assets -> androidAssets
 * src/androidMain/aidl -> androidAidl
 * src/${targetName}Main/kotlin -> ${targetName}Main
 * src/${targetName}Main/resources -> ${targetName}Resources
 * src/${targetName}Test/kotlin -> ${targetName}Test
 * src/${targetName}Test/resources -> ${targetName}TestResources
 * ```
 *
 * `${targetName}` 可以是 `common`, `android` `desktop` 等.
 */
fun Project.configureFlattenMppSourceSets() {
    kotlinSourceSets?.invoke {
        fun setForTarget(
            targetName: String,
        ) {
            findByName("${targetName}Main")?.apply {
                resources.srcDirs(listOf(projectDir.resolve("${targetName}Resources")))
                kotlin.srcDirs(listOf(projectDir.resolve("${targetName}Main"), projectDir.resolve(targetName)))
            }
            findByName("${targetName}Test")?.apply {
                resources.srcDirs(listOf(projectDir.resolve("${targetName}TestResources")))
                kotlin.srcDirs(listOf(projectDir.resolve("${targetName}Test")))
            }
        }

        setForTarget("common")

        allKotlinTargets().configureEach {
            val targetName = name
            setForTarget(targetName)
        }
    }
}

configureFlattenSourceSets()
configureFlattenMppSourceSets()