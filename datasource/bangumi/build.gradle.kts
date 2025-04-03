/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

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
    namespace = "me.him188.ani.datasources.bangumi"
}

val generatedRoot = "generated/openapi"

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.datasource.datasourceApi)
        api(libs.kotlinx.datetime)
        api(libs.kotlinx.coroutines.core)
        api(libs.androidx.collection)

        implementation(projects.utils.coroutines)
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

// https://github.com/OpenAPITools/openapi-generator/blob/master/modules/openapi-generator-gradle-plugin/README.adoc
val generateApiV0 = tasks.register("generateApiV0", GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/v0.yaml")
    outputDir.set(layout.buildDirectory.file(generatedRoot).get().asFile.absolutePath)
    packageName.set("me.him188.ani.datasources.bangumi")
    modelNamePrefix.set("Bangumi")
    apiNameSuffix.set("BangumiApi")
    // https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/kotlin.md
    additionalProperties.set(
        mapOf(
            "apiSuffix" to "BangumiApi",
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

//    typeMappings.put("BangumiValue", "kotlinx.serialization.json.JsonElement")
//    schemaMappings.put("WikiV0", "kotlinx.serialization.json.JsonElement") // works
//    schemaMappings.put("Item", "kotlinx.serialization.json.JsonElement")
//    schemaMappings.put("Value", "kotlinx.serialization.json.JsonElement")
    typeMappings.put(
        "kotlin.Double",
        "@Serializable(me.him188.ani.utils.serialization.BigNumAsDoubleStringSerializer::class) me.him188.ani.utils.serialization.BigNum",
    )
//    typeMappings.put("BangumiEpisodeCollectionType", "/*- `0`: 未收藏 - `1`: 想看 - `2`: 看过 - `3`: 抛弃*/ Int")
}

val stripeApiP1 = tasks.register("stripeApiP1") {
    val strippedP1File = layout.buildDirectory.file("p1-stripped.yaml")
    val inputFile = file("$projectDir/p1.yaml")
    inputs.file(inputFile)
    outputs.file(strippedP1File)

    /**
     * 我们只需要保留吐槽箱相关的 API
     */
    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        val p1ApiObject: Map<String, Any> = inputFile.inputStream().use { yaml.load(it) }

        // keep subjects only
        val paths = p1ApiObject["paths"].cast<Map<String, *>>().toMutableMap()
        val keepPaths = listOf(
            "/p1/episodes/-/comments", // 条目剧集的吐槽箱, 作为剧集评论
            "/p1/episodes/{episodeID}", // 条目剧集的吐槽箱, 作为剧集评论
            "/p1/subjects/{subjectID}/comments", // 条目吐槽箱, 作为条目评论
            "/p1/trending/subjects",
            "/p1/collections/subjects",
        )
        val subjectPaths = paths.filter { (path, _) -> keepPaths.any { path.startsWith(it) } }
        println("The following paths are kept: ${subjectPaths.keys}")

        // keep components referred by subjects only
        val components = p1ApiObject["components"].cast<Map<String, *>>().toMutableMap()
        components.remove("securitySchemes")
        val keepSchemaKeys = listOf(
            "ErrorResponse",
            "UpdateContent",
            "Episode",
            "Reaction",
            "SlimUser",
            "CommentBase",
            "CreateReply",
            "TurnstileToken",

            "CollectionType",
            "SubjectInterestComment",
            "Reaction",
            "CollectionType",
            "SlimUser",
            "Avatar",
            "SimpleUser",

            "EpisodeCollectionStatus",
            "SlimSubject",
            "EpisodeType",
            "SubjectRating",
            "SubjectType",
            "SubjectImages",
            "SlimSubjectInterest",

            "TrendingSubject",

            "CollectSubject",
            "UpdateSubjectProgress",
            "Subject",
            "Subject.*",
            "Infobox",
            "Slim.*",
            "PersonImages",
            "Reply.*",
            "WikiPlatform",
        )
        val schemas = components["schemas"].cast<Map<String, *>>().toMutableMap()
        val keepSchemas = schemas.filter { (component, _) ->
            keepSchemaKeys.any {
                Regex(it).matchEntire(component) != null
            }
        }

        val strippedApiObject = mutableMapOf<String, Any>().apply {
            put("openapi", p1ApiObject["openapi"].cast())
            put("info", p1ApiObject["info"].cast())
            put("paths", subjectPaths)
            put("components", mapOf("schemas" to keepSchemas))
        }

        strippedP1File.get().asFile.writeText(yaml.dump(strippedApiObject))
    }
}

val generateApiP1 = tasks.register("generateApiP1", GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(stripeApiP1.get().outputs.files.singleFile.absolutePath)
    outputDir.set(layout.buildDirectory.file(generatedRoot).get().asFile.absolutePath)
    packageName.set("me.him188.ani.datasources.bangumi.next")
    modelNamePrefix.set("BangumiNext")
    apiNameSuffix.set("BangumiNextApi")
    additionalProperties.set(
        mapOf(
            "apiSuffix" to "BangumiNextApi",
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",
            "enumPropertyNaming" to "UPPERCASE",
            "omitGradleWrapper" to "true",
            "generateOneOfAnyOfWrappers" to "true",
        ),
    )
    generateModelTests.set(false)
    generateApiTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    validateSpec.set(false)

    typeMappings.put(
        "kotlin.Double",
        "@Serializable(me.him188.ani.utils.serialization.BigNumAsDoubleStringSerializer::class) me.him188.ani.utils.serialization.BigNum",
    )

    dependsOn(stripeApiP1)
}

val fixGeneratedOpenApi = tasks.register("fixGeneratedOpenApi") {
    dependsOn(generateApiV0, generateApiP1)
    val models =
        layout.buildDirectory.file("$generatedRoot/src/commonMain/kotlin/me/him188/ani/datasources/bangumi/models/")
            .get().asFile


//    inputs.file(file)
//    outputs.file(file)
    //    outputs.upToDateWhen {
//        models.resolve("BangumiValue.kt").readText() == expected
//    }
    doLast {
        models.resolve("BangumiValue.kt").writeText(
            """
                package me.him188.ani.datasources.bangumi.models
                
                typealias BangumiValue = kotlinx.serialization.json.JsonElement
            """.trimIndent(),
        )
        models.resolve("BangumiEpisodeCollectionType.kt").delete()
        models.resolve("BangumiSubjectCollectionType.kt").delete()
        models.resolve("BangumiSubjectType.kt").delete()
    }
}

val copyGeneratedToSrc = tasks.register("copyGeneratedToSrc", Copy::class) {
    dependsOn(fixGeneratedOpenApi)
    from(layout.buildDirectory.file("$generatedRoot/src/commonMain/kotlin"))
    into("src/commonMain/gen")
}

//tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//    dependsOn(fixGeneratedOpenApi)
//}

tasks.register("generateOpenApi") {
    dependsOn(copyGeneratedToSrc)
}
