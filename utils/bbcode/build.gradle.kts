/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

plugins {
    id("ani.kmp-library")
    alias(libs.plugins.antlr.kotlin)
    idea
}

val generatedRoot = projectDir.resolve("src/commonMain/generatedKotlin")

kotlin {
    android {
        namespace = "me.him188.ani.utils.bbcode"
    }
    sourceSets.commonMain {
        dependencies {
            // antlr kotlin
            implementation(libs.antlr.kotlin.runtime)
        }
        kotlin.srcDirs(generatedRoot)
    }
}

idea {
    module {
        generatedSourceDirs.add(generatedRoot)
    }
}

val generateBBCodeGrammarSource = tasks.register<AntlrKotlinTask>("generateBBCodeGrammarSource") {
    source = fileTree(layout.projectDirectory) {
        include("BBCode.g4")
    }

    packageName = "me.him188.ani.utils.bbcode"
    arguments = listOf("-visitor")

    outputDirectory = generatedRoot
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool> {
    mustRunAfter(generateBBCodeGrammarSource)
}