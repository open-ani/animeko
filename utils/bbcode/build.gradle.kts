/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask

/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `ani-mpp-lib-targets`
    alias(libs.plugins.antlr.kotlin)
    idea
}

val generatedRoot = projectDir.resolve("src/commonMain/generatedKotlin")

kotlin {
    androidLibrary {
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