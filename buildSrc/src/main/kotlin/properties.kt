/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import java.util.Properties

/**
 * 读取 `local.properties` 的 [ValueSource].
 *
 * 为什么用 ValueSource 而不是直接 `File.readText()`:
 * Gradle 只把 ValueSource 的**返回值**登记为配置缓存输入, 文件内容真正变化时才会让缓存失效;
 * 而在配置期直接读文件 / 调用 `File.exists()` 会被登记成一堆零散的 file-system 输入,
 * 使缓存失效条件变得不可预测.
 *
 * [obtain] 必须足够快 —— 每次构建都会执行一次, 用来判断配置缓存能否复用.
 */
abstract class LocalPropertiesValueSource :
    ValueSource<Map<String, String>, LocalPropertiesValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val localPropertiesFile: RegularFileProperty
    }

    override fun obtain(): Map<String, String> {
        val file = parameters.localPropertiesFile.get().asFile
        // 文件不存在就返回空. 历史实现会在这里 createNewFile(), 那是配置期副作用
        // (它真的在仓库里留下过一个空的 buildSrc/local.properties).
        if (!file.isFile) return emptyMap()
        val properties = Properties()
        file.inputStream().buffered().use(properties::load)
        return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
    }
}

/**
 * `local.properties` 的全部内容. 由 Gradle 缓存, 每次构建只解析一次.
 *
 * 用 `layout.settingsDirectory` 而不是 `rootProject.file(...)`, 避免跨项目访问.
 */
val Project.localProperties: Provider<Map<String, String>>
    get() = providers.of(LocalPropertiesValueSource::class) {
        parameters.localPropertiesFile.set(layout.settingsDirectory.file("local.properties"))
    }

/**
 * 按 `local.properties` -> `-D` 系统属性 -> 环境变量 -> Gradle property 的顺序查找.
 *
 * 全链路都是 provider, 因此每一个来源都会被登记为配置缓存输入.
 */
fun Project.aniProperty(name: String): Provider<String> =
    localProperties.map { it[name] }
        .orElse(providers.systemProperty(name))
        .orElse(providers.environmentVariable(name))
        .orElse(providers.gradleProperty(name))

fun Project.getProperty(name: String) =
    getPropertyOrNull(name) ?: error("Property $name not found")

fun Project.getPropertyOrNull(name: String): String? =
    aniProperty(name).orNull
    // extra 是项目本地的可变状态, 不是外部输入, 因此不需要 (也不能) 走 provider.
        ?: extensions.extraProperties.runCatching { get(name).toString() }.getOrNull()

fun Project.getLocalProperty(key: String): String? = localProperties.get()[key]

fun Project.getIntProperty(name: String) = getProperty(name).toInt()

val Project.enableAnitorrent
    get() = (getPropertyOrNull("ani.enable.anitorrent") ?: "false").toBooleanStrict()

val Project.enableIos
    get() = getPropertyOrNull("ani.enable.ios")?.toBooleanStrict() ?: false

val Project.buildIosFramework
    get() = getPropertyOrNull("ani.build.framework")?.toBooleanStrict() ?: false

val Project.enableFirebase
    get() = getPropertyOrNull("ani.enable.firebase")?.toBooleanStrict() ?: false
