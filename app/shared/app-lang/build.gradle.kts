/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

plugins {
    id("ani.kmp-compose")
    alias(libs.plugins.kotlin.plugin.serialization)

    // org.jetbrains.kotlinx.atomicfu
    idea
}

kotlin {
    androidLibrary {
        namespace = "me.him188.ani.app.ui.lang"
    }
    sourceSets.commonMain.dependencies {
        implementation(libs.atomicfu)
        api(libs.compose.components.resources)
    }
    sourceSets.commonTest.dependencies {
    }
    sourceSets.androidMain.dependencies {
    }
    sourceSets.desktopMain.dependencies {
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "me.him188.ani.app.ui.lang"
    customDirectory(
        "commonMain",
        project.provider {
            project.layout.projectDirectory.dir("src/androidMain/res")
        },
    )
}

val populateStringsLocales by tasks.registering(Copy::class) {
    group = "ani"
    description =
        "Populate string resources for locales speaking Simplified or Traditional Chinese."

    val chtLocales = listOf(
        "values-zh-rMO",
    )
    val chsLocales = listOf(
        "values-zh",
        "values-zh-rSG",
    )
    destinationDir = file("src/androidMain/res")

    for (file in file("src/androidMain/res/values").listFiles().orEmpty()) {
        if (file.isFile && file.name.startsWith("strings") && file.extension == "xml") {
            for (locale in chtLocales) {
                from(file("src/androidMain/res/values-zh-rHK/${file.name}")) {
                    into(locale)
                    rename { file.name }
                }
            }

            for (locale in chsLocales) {
                from(file("src/androidMain/res/values-zh-rCN/${file.name}")) {
                    into(locale)
                    rename { file.name }
                }
            }
        }
    }

}

tasks.matching {
    // desktop, `generateResourceAccessorsForCommonMain`
    it.name.startsWith("convertXmlValueResources")
            || it.name.startsWith("copyNonXmlValueResources")
            // android, `generateDebugResources`
            || (it.name.startsWith("generate") && it.name.endsWith("Resources"))
            || it.name.startsWith("extractDeepLinks")
            || (it.name.startsWith("map") && it.name.endsWith("SourceSetPaths")) // mapReleaseSourceSetPaths
}.configureEach {
    dependsOn(populateStringsLocales)
}

idea {
    module {
        excludeDirs.add(file("src/androidMain/res/values-zh"))
        excludeDirs.add(file("src/androidMain/res/values-zh-rMO"))
        excludeDirs.add(file("src/androidMain/res/values-zh-rSG"))
    }
}
