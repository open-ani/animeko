import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.Serializable

/**
 * Configuration extension for BuildConfig generation
 */
abstract class BuildConfigExtension {
    abstract val packageName: Property<String>
    abstract val className: Property<String>
    abstract val outputDir: DirectoryProperty

    private val platformConfigs = mutableMapOf<String, BuildConfigPlatform>()

    fun platform(name: String, configure: BuildConfigPlatform.() -> Unit) {
        val platform = platformConfigs.getOrPut(name) { BuildConfigPlatform(name) }
        platform.configure()
    }

    internal fun getPlatformConfigs(): Map<String, BuildConfigPlatform> = platformConfigs.toMap()
}

/**
 * Platform-specific configuration
 */
class BuildConfigPlatform(val name: String) {
    private val fields = mutableMapOf<String, BuildConfigField>()

    fun stringField(name: String, value: String) {
        fields[name] = BuildConfigField.StringField(name, value)
    }

    fun booleanField(name: String, value: Boolean) {
        fields[name] = BuildConfigField.BooleanField(name, value)
    }

    fun integerField(name: String, value: Int) {
        fields[name] = BuildConfigField.IntegerField(name, value)
    }

    fun expressionField(name: String, expression: String) {
        fields[name] = BuildConfigField.ExpressionField(name, expression)
    }

    internal fun getFields(): Map<String, BuildConfigField> = fields.toMap()
}

/**
 * Represents different types of build config fields
 */
sealed class BuildConfigField(val name: String) : Serializable {
    abstract fun generateKotlinCode(): String

    data class StringField(val fieldName: String, val value: String) : BuildConfigField(fieldName) {
        override fun generateKotlinCode(): String = "override val $name = \"$value\""
    }

    data class BooleanField(val fieldName: String, val value: Boolean) : BuildConfigField(fieldName) {
        override fun generateKotlinCode(): String = "override val $name = $value"
    }

    data class IntegerField(val fieldName: String, val value: Int) : BuildConfigField(fieldName) {
        override fun generateKotlinCode(): String = "override val $name = $value"
    }

    data class ExpressionField(val fieldName: String, val expression: String) : BuildConfigField(fieldName) {
        override fun generateKotlinCode(): String = "override val $name = $expression"
    }
}

/**
 * Task for generating BuildConfig files
 */
@CacheableTask
abstract class GenerateBuildConfigTask : DefaultTask() {

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val className: Property<String>

    @get:Input
    abstract val platformName: Property<String>

    @get:Input
    abstract val fields: MapProperty<String, BuildConfigField>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateBuildConfig() {
        val outputDir = outputDirectory.get().asFile
        outputDir.mkdirs()

        val outputFile = File(outputDir, "${className.get()}.kt")
        val packageNameValue = packageName.get()
        val classNameValue = className.get()
        val platformNameValue = platformName.get()

        val fieldsCode = fields.get().values.joinToString("\n    ") { field ->
            field.generateKotlinCode()
        }

        val content = """
            package $packageNameValue
            
            object ${classNameValue}${platformNameValue.replaceFirstChar { it.uppercase() }} : $classNameValue {
                $fieldsCode
            }
        """.trimIndent()

        outputFile.writeText(content)
    }
}

/**
 * Plugin for BuildConfig generation
 */
class BuildConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("buildConfig", BuildConfigExtension::class.java)

        // Set default values
        extension.packageName.convention("buildconfig")
        extension.className.convention("BuildConfig")
        extension.outputDir.convention(project.layout.buildDirectory.dir("generated/buildconfig"))

        project.afterEvaluate {
            val platformConfigs = extension.getPlatformConfigs()
            val createdTasks = mutableSetOf<String>()

            platformConfigs.forEach { (platformName, platformConfig) ->
                val taskName = "generate${extension.className.get()}${platformName.replaceFirstChar { it.uppercase() }}"

                val generateTask = project.tasks.register<GenerateBuildConfigTask>(taskName) {
                    group = "build"
                    description = "Generates BuildConfig for $platformName platform"

                    packageName.set(extension.packageName)
                    className.set(extension.className)
                    this.platformName.set(platformName)

                    // Convert platform fields to a map for task input
                    val fieldMap = platformConfig.getFields()
                    fields.set(fieldMap)

                    outputDirectory.set(extension.outputDir.dir(platformName))
                }

                createdTasks.add(taskName)

                // Configure Kotlin source sets if available
                configureKotlinSourceSets(project, platformName, extension.outputDir.dir(platformName))

                // Set up task dependencies
                configurePlatformTaskDependencies(project, platformName, taskName)
            }

            // Create placeholder tasks for platforms that might be referenced but not configured
            createPlaceholderTasks(project, extension.className.get(), createdTasks)
        }
    }

    private fun configureKotlinSourceSets(
        project: Project,
        platformName: String,
        outputDir: org.gradle.api.provider.Provider<org.gradle.api.file.Directory>
    ) {
        // Try to find Kotlin Multiplatform extension
        val kotlinExtension = project.extensions.findByType<KotlinMultiplatformExtension>()
        if (kotlinExtension != null) {
            val sourceSetName = "${platformName}Main"
            try {
                val sourceSet = kotlinExtension.sourceSets.getByName(sourceSetName)
                sourceSet.kotlin.srcDirs(outputDir)
                project.extensions.findByType<IdeaModel>()?.module?.generatedSourceDirs?.add(outputDir.get().asFile)
                project.logger.info("Added BuildConfig output directory to $sourceSetName source set")
            } catch (e: Exception) {
                project.logger.debug("Could not find source set $sourceSetName: ${e.message}")
            }
        } else {
            project.logger.debug("Kotlin Multiplatform extension not found")
        }
    }

    private fun configurePlatformTaskDependencies(project: Project, platformName: String, generateTaskName: String) {
        // Configure dependencies using task name patterns and types
        project.tasks.configureEach {
            when {
                // Desktop compilation tasks
                platformName.lowercase() == "desktop" && (
                        name == "compileKotlinDesktop" ||
                                name.contains("compileKotlinDesktop")
                        ) -> {
                    dependsOn(generateTaskName)
                }

                // Android compilation tasks
                platformName.lowercase() == "android" && (
                        name.contains("compileKotlinAndroid") ||
                                name.contains("KotlinAndroid")
                        ) -> {
                    dependsOn(generateTaskName)
                }

                // iOS compilation tasks
                platformName.lowercase() == "ios" && (
                        name.contains("compileKotlinIos") ||
                                name.contains("KotlinIos")
                        ) -> {
                    dependsOn(generateTaskName)
                }

                // Generic platform compilation tasks
                name.contains("compileKotlin${platformName.replaceFirstChar { it.uppercase() }}") -> {
                    dependsOn(generateTaskName)
                }
            }
        }

        // Also configure KotlinCompile tasks specifically for iOS
        if (platformName.lowercase() == "ios") {
            project.tasks.withType(KotlinCompile::class.java) {
                if (name.contains("ios", ignoreCase = true)) {
                    dependsOn(generateTaskName)
                }
            }
        }
    }

    private fun createPlaceholderTasks(project: Project, className: String, createdTasks: Set<String>) {
        val commonPlatforms = listOf("desktop", "ios", "android", "js")

        commonPlatforms.forEach { platform ->
            val taskName = "generate${className}${platform.replaceFirstChar { it.uppercase() }}"
            if (!createdTasks.contains(taskName)) {
                project.tasks.register(taskName) {
                    group = "build"
                    description = "Placeholder task for $platform platform (not configured)"
                    // Add a dummy input to make the task cacheable
                    inputs.property("platform", platform)
                }
            }
        }
    }
}

apply<BuildConfigPlugin>()
