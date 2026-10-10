/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

// 手机远程配置电视: 协议模型、手机端会话与生成的 HTTP 客户端. 电视端服务在 :app:tv-remote-settings-server.

plugins {
    id("ani.kmp-library")
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.openapi.generator)
}

kotlin {
    android {
        namespace = "me.him188.ani.app.remote.settings"
    }
    sourceSets.commonMain { kotlin.srcDir("src/commonMain/generated") }
    sourceSets.commonMain.dependencies {
        api(projects.app.shared.appData)
        api(projects.app.shared.remoteSettingsContract)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.serialization.kotlinx.json)
    }
    sourceSets.commonTest.dependencies {
        implementation(libs.ktor.client.mock)
    }
}

// The client shares the app's existing serializable settings and datasource models.
val generateRemoteSettingsApi by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(layout.projectDirectory.file("../remote-settings-contract/openapi.json").asFile.path)
    outputDir.set(layout.buildDirectory.dir("remote-settings-openapi").get().asFile.path)
    packageName.set("me.him188.ani.remote.settings.generated")
    additionalProperties.set(mapOf("library" to "multiplatform", "dateLibrary" to "kotlinx-datetime"))
    for (name in listOf("SettingsSnapshot", "PreferenceRequest", "MediaSourceRequest", "DanmakuFilterRequest", "BackupRequest", "OperationResult")) {
        schemaMappings.put(name, name)
        importMappings.put(name, "me.him188.ani.app.domain.settings.remote.$name")
    }
    globalProperties.set(mapOf("apis" to "", "models" to "PingRequest,PingResponse,RemoteError,LogSnapshot", "supportingFiles" to ""))
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
}

val generateRemoteSettingsClient = tasks.register<Sync>("generateRemoteSettingsClient") {
    dependsOn(generateRemoteSettingsApi)
    from(layout.buildDirectory.dir("remote-settings-openapi/src/commonMain/kotlin"))
    into(layout.projectDirectory.dir("src/commonMain/generated"))
}

// Generated sources are checked in; generation runs only when explicitly requested.
tasks.matching { it.name.startsWith("compile") }.configureEach {
    mustRunAfter(generateRemoteSettingsClient)
}

val remoteSchemaCompilation = kotlin.targets.getByName("desktop").compilations.getByName("test")
tasks.register<JavaExec>("generateRemoteSettingsOpenApi") {
    dependsOn(remoteSchemaCompilation.compileTaskProvider)
    classpath = files(remoteSchemaCompilation.output.allOutputs, remoteSchemaCompilation.runtimeDependencyFiles)
    mainClass.set("me.him188.ani.app.domain.settings.remote.GenerateRemoteSettingsOpenApi")
    args(layout.projectDirectory.file("../remote-settings-contract/openapi.json").asFile.absolutePath)
}
