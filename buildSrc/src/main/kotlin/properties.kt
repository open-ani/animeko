/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.Project
import java.io.File
import java.util.Properties

fun Project.getProperty(name: String) =
    getPropertyOrNull(name) ?: error("Property $name not found")

fun Project.getPropertyOrNull(name: String) =
    getLocalProperty(name)
        ?: System.getProperty(name)
        ?: System.getenv(name)
        ?: findProperty(name)?.toString()
        ?: properties[name]?.toString()
        ?: extensions.extraProperties.runCatching { get(name).toString() }.getOrNull()


val Project.localPropertiesFile: File get() = project.rootProject.file("local.properties")

fun Project.getLocalProperty(key: String): String? {
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


fun Project.getIntProperty(name: String) = getProperty(name).toInt()

val Project.enableAnitorrent
    get() = (getPropertyOrNull("ani.enable.anitorrent") ?: "false").toBooleanStrict()

val Project.enableIos
    get() = getPropertyOrNull("ani.enable.ios")?.toBooleanStrict() ?: true
