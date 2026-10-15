/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// 桌面发布包的清单生成与自动更新端到端测试. 不依赖 Gradle, 所以能打成独立 jar 在 CI 的 Verify job 里直接运行.
plugins {
    id("ani.jvm-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.compress) // 写带 unix 权限的 zip, 与 Gradle 的 Zip 任务产出同形
}

// CI 的 Build job 上传它, Verify job 下载后 `java -jar desktop-update-e2e.jar --app <产物目录> --work-dir <目录>`.
tasks.register("standaloneJar", Jar::class) {
    group = "build"
    description = "Self-contained jar with the manifest generator and the update E2E runner"
    archiveFileName.set("desktop-update-e2e.jar")
    manifest.attributes["Main-Class"] = "me.him188.ani.tools.updatee2e.MainKt"
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
}
