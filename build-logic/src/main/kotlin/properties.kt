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
 * 读取 `local.properties`. 用 ValueSource 让 Gradle 只把返回值登记为配置缓存输入,
 * 而不是配置期直接读文件产生的一堆零散 file-system 输入.
 */
abstract class LocalPropertiesValueSource :
    ValueSource<Map<String, String>, LocalPropertiesValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val localPropertiesFile: RegularFileProperty
    }

    override fun obtain(): Map<String, String> {
        val file = parameters.localPropertiesFile.get().asFile
        // 不存在就返回空. 历史实现在这里 createNewFile(), 那是配置期副作用.
        if (!file.isFile) return emptyMap()
        val properties = Properties()
        file.inputStream().buffered().use(properties::load)
        return properties.entries.associate { (key, value) -> key.toString() to value.toString() }
    }
}

/** `local.properties` 全部内容, 每次构建只解析一次. 用 settingsDirectory 避免跨项目访问. */
val Project.localProperties: Provider<Map<String, String>>
    get() = providers.of(LocalPropertiesValueSource::class) {
        parameters.localPropertiesFile.set(layout.settingsDirectory.file("local.properties"))
    }

/** 按 local.properties -> 系统属性 -> 环境变量 -> Gradle property 查找, 全链路 provider. */
fun Project.aniProperty(name: String): Provider<String> =
    localProperties.map { it[name] }
        .orElse(providers.systemProperty(name))
        .orElse(providers.environmentVariable(name))
        .orElse(providers.gradleProperty(name))

fun Project.getProperty(name: String) =
    getPropertyOrNull(name) ?: error("Property $name not found")

fun Project.getPropertyOrNull(name: String): String? =
    aniProperty(name).orNull
    // extra 是项目本地可变状态, 不是外部输入, 不走 provider.
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
