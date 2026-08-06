/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import javax.inject.Inject

/**
 * 每个平台一份的 BuildConfig 字段. 用 ListProperty 而非 Map: 平台对象先创建、字段后添加,
 * 惰性求值才能免掉 afterEvaluate.
 */
abstract class BuildConfigPlatform @Inject constructor(private val platformName: String) : Named {
    override fun getName(): String = platformName

    abstract val fieldLines: ListProperty<String>

    private fun addField(name: String, isOverride: Boolean, valueLiteral: String) {
        val prefix = if (isOverride) "override " else ""
        fieldLines.add("${prefix}val $name = $valueLiteral")
    }

    fun stringField(name: String, value: String?, isOverride: Boolean = true) {
        addField(name, isOverride, if (value == null) "null" else "\"$value\"")
    }

    fun booleanField(name: String, value: Boolean, isOverride: Boolean = true) {
        addField(name, isOverride, value.toString())
    }

    fun integerField(name: String, value: Int, isOverride: Boolean = true) {
        addField(name, isOverride, value.toString())
    }

    fun expressionField(name: String, expression: String, isOverride: Boolean = true) {
        addField(name, isOverride, expression)
    }
}

abstract class BuildConfigExtension @Inject constructor(objects: ObjectFactory) {
    abstract val packageName: Property<String>
    abstract val className: Property<String>
    abstract val outputDir: DirectoryProperty

    val platforms: NamedDomainObjectContainer<BuildConfigPlatform> =
        objects.domainObjectContainer(BuildConfigPlatform::class.java)

    fun platform(name: String, configure: BuildConfigPlatform.() -> Unit) {
        platforms.maybeCreate(name).configure()
    }
}

@CacheableTask
abstract class GenerateBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val className: Property<String>

    @get:Input
    abstract val platformName: Property<String>

    /** 已渲染好的代码行, 纯 String, 任务状态里没有自定义序列化. */
    @get:Input
    abstract val fieldLines: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateBuildConfig() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val classNameValue = className.get()
        val platformNameValue = platformName.get()
        val fieldsCode = fieldLines.get().joinToString("\n    ")

        val content = """
package ${packageName.get()}

object ${classNameValue}${platformNameValue.replaceFirstChar { it.uppercase() }} : $classNameValue {
    $fieldsCode
}
""".trim() + "\n"

        outputDir.resolve("$classNameValue.kt").writeText(content)
    }
}

/** 生成各平台的 BuildConfig. */
class AniBuildConfigPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val extension = extensions.create<BuildConfigExtension>("buildConfig")
        extension.packageName.convention("buildconfig")
        extension.className.convention("BuildConfig")
        extension.outputDir.convention(layout.buildDirectory.dir("generated/buildconfig"))

        // 必须对后加入的元素生效, 所以用 all 而不是 configureEach.
        extension.platforms.all {
            val platform = this
            val platformDir = extension.outputDir.map { it.dir(platform.name) }

            val generateTask = tasks.register<GenerateBuildConfigTask>(
                "generate${extension.className.get()}${platform.name.replaceFirstChar { it.uppercase() }}",
            ) {
                group = "build"
                description = "Generates ${extension.className.get()} for ${platform.name}"

                packageName.set(extension.packageName)
                className.set(extension.className)
                platformName.set(platform.name)
                fieldLines.set(platform.fieldLines)
                outputDirectory.set(platformDir)
            }

            // 把生成目录接进 `${platform}Main` 源集. 这样 Gradle 会自己推导出
            // "编译该源集之前先跑生成任务", 不需要任何按名字写死的 dependsOn.
            plugins.withType(KotlinBasePlugin::class.java) {
                extensions.findByType(KotlinMultiplatformExtension::class.java)
                    ?.sourceSets
                    ?.matching { it.name == "${platform.name}Main" }
                    ?.configureEach {
                        kotlin.srcDir(generateTask.flatMap { it.outputDirectory })
                    }
            }
        }
    }
}
