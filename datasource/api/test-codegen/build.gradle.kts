/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
    `flatten-source-sets`
    idea
}

dependencies {
    implementation(projects.datasource.datasourceApi)
    implementation(projects.utils.ktorClient)
    implementation(projects.datasource.dmhy)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.slf4j.simple)
    implementation(libs.jsoup)
    implementation(libs.kotlinpoet)
}

idea {
    module.excludeDirs.add(file("testData")) // avoid indexing large files
}