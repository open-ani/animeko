/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.Project

val Project.composeOrNull
    get() = (this as org.gradle.api.plugins.ExtensionAware).extensions.findByName("compose") as org.jetbrains.compose.ComposeExtension?
/*
*/
/**
 * Apply compose stability analyzer plugin to subprojects whose name starts with "ui-".
 * Should be called in root project's `subprojects` block with `afterEvaluate`.
 *//*
fun Project.configureComposeStabilityAnalyzer() {
    if (project.name.startsWith("ui-")) {
        project.pluginManager.apply("com.github.skydoves.compose.stability.analyzer")
    }
}*/
