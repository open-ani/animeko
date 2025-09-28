/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import de.undercouch.gradle.tasks.download.Download
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

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
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    idea
    `ani-mpp-lib-targets`
    alias(libs.plugins.openapi.generator)
}

android {
    namespace = "me.him188.ani.client"
}

val generatedRoot = "generated/openapi"

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.datasource.datasourceApi)
        api(libs.kotlinx.datetime)
        api(libs.kotlinx.coroutines.core)

        implementation(projects.utils.serialization)
        implementation(libs.ktor.client.logging)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
    }
    sourceSets.commonMain {
        kotlin.srcDirs(file("src/commonMain/gen"))
    }
}

idea {
    module {
        generatedSourceDirs.add(file("src/commonMain/gen"))
    }
}

val apiServer = getPropertyOrNull("ani.api.server")?.takeIf { it.isNotBlank() }
    ?: "https://api.animeko.org"

val downloadSpec = tasks.register<Download>("downloadSpec") {
    src("$apiServer/openapi.json")
    dest(layout.buildDirectory.file("temp/downloadSpec/openapi.json").get())
    onlyIfModified(false)
    overwrite(true)
}

// https://github.com/OpenAPITools/openapi-generator/blob/master/modules/openapi-generator-gradle-plugin/README.adoc
val generateApi = tasks.register("generateApiV0", GenerateTask::class) {
    dependsOn(downloadSpec)
    generatorName.set("kotlin")
    inputSpec.set(downloadSpec.map { it.dest.absolutePath })
    outputDir.set(layout.buildDirectory.file(generatedRoot).map { it.asFile.absolutePath })
    packageName.set("me.him188.ani.client")
    modelNamePrefix.set("Ani")
    apiNameSuffix.set("Ani")
    removeOperationIdPrefix = true
    // https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/kotlin.md
    additionalProperties.set(
        mapOf(
            "apiSuffix" to "AniApi",
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",
//            "serializationLibrary" to "kotlinx_serialization", // 加了这个他会生成两个 `@Serializable`
            "enumPropertyNaming" to "UPPERCASE",
//            "generateOneOfAnyOfWrappers" to "true",
            "omitGradleWrapper" to "true",
        ),
    )
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    validateSpec.set(false)

//    typeMappings.put("BangumiValue", "kotlinx.serialization.json.JsonElement")
//    schemaMappings.put("WikiV0", "kotlinx.serialization.json.JsonElement") // works
//    schemaMappings.put("Item", "kotlinx.serialization.json.JsonElement")
//    schemaMappings.put("Value", "kotlinx.serialization.json.JsonElement")
//    typeMappings.put(
//        "kotlin.Double",
//        "@Serializable(me.him188.ani.utils.serialization.BigNumAsDoubleStringSerializer::class) me.him188.ani.utils.serialization.BigNum",
//    )
//    typeMappings.put("BangumiEpisodeCollectionType", "/*- `0`: 未收藏 - `1`: 想看 - `2`: 看过 - `3`: 抛弃*/ Int")
}

val fixGeneratedOpenApi = tasks.register("fixGeneratedOpenApi") {
    dependsOn(generateApi)
    val outputDir = file(generateApi.get().outputDir.get())

    doLast {
        outputDir.walk().filter { it.isFile }.forEach {
            var text = it.readText()
            val original = text
            text = text.replace(
                """
                    open suspend fun uploadAvatar(body: me.him188.ani.client.infrastructure.OctetByteArray): HttpResponse<kotlin.Any> {
                """.trimIndent(),
                """
                    open suspend fun uploadAvatar(body: io.ktor.http.content.OutgoingContent): HttpResponse<kotlin.Any> {
                """.trimIndent()
            )

            val off = "off" // 防止 IDE 把我们这个代码识别成指令
            if (!text.contains("// @formatter:$off")) {
                text = "// @formatter:$off\n$text\n// @formatter:on\n"
            }

            if (text != original) {
                it.writeText(text)
            }
        }
    }
}

val copyGeneratedToSrc = tasks.register("copyGeneratedToSrc", Copy::class) {
    dependsOn(fixGeneratedOpenApi)
    from(layout.buildDirectory.file("$generatedRoot/src/commonMain/kotlin"))
    into("src/commonMain/gen")
}

tasks.register("generateOpenApiForAnimeko") {
    dependsOn(copyGeneratedToSrc)
}
