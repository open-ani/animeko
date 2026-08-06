/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 原先这里有一长串 `alias(...) apply false`, 只为把插件塞进根 buildscript classpath 供子项目继承.
// 改用 build-logic 后, 插件由 settings 的 includeBuild 或 catalog 里的版本解析, 不再需要.
plugins {
    idea
}

// 仓库搬到 settings 的 dependencyResolutionManagement, 其余约定搬到 build-logic 的 `ani.base`.
group = "me.him188.ani"
version = providers.gradleProperty("version.name").get()

idea {
    module {
        excludeDirs.add(file(".kotlin"))
    }
}

// Note: this task does not support configuration cache
tasks.register("downloadAllDependencies") {
    notCompatibleWithConfigurationCache("Filters configurations at execution time")
    description = "Resolves every resolvable configuration in every project"
    group = "help"

    doLast {
        rootProject.allprojects.forEach { p ->
            p.configurations
                .filter { it.isCanBeResolved }
                .forEach {
                    runCatching { it.resolve() }
                }
        }
    }
}
