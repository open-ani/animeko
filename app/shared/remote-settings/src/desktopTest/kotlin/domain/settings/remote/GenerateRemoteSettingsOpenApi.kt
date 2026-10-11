/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.him188.ani.remote.settings.generated.models.LogSnapshot
import me.him188.ani.remote.settings.generated.models.PingRequest
import me.him188.ani.remote.settings.generated.models.PingResponse
import me.him188.ani.remote.settings.generated.models.RemoteError

/** Builds wire schemas from the serializers actually used by the client and server. */
@OptIn(ExperimentalSerializationApi::class)
object GenerateRemoteSettingsOpenApi {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = File(args.single())
        val document = Json.parseToJsonElement(file.readText()).jsonObject
        val components = document.getValue("components").jsonObject
        val schemas = generateSchemas()
        val updated =
            JsonObject(document + ("components" to JsonObject(components + ("schemas" to schemas))))
        file.writeText(
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), updated) + "\n"
        )
    }

    fun generateSchemas(): JsonObject =
        DescriptorSchemas(
                "SettingsSnapshot" to SettingsSnapshot.serializer().descriptor,
                "PreferenceRequest" to PreferenceRequest.serializer().descriptor,
                "MediaSourceRequest" to MediaSourceRequest.serializer().descriptor,
                "DanmakuFilterRequest" to DanmakuFilterRequest.serializer().descriptor,
                "BackupRequest" to BackupRequest.serializer().descriptor,
                "OperationResult" to OperationResult.serializer().descriptor,
                "PingRequest" to PingRequest.serializer().descriptor,
                "PingResponse" to PingResponse.serializer().descriptor,
                "RemoteError" to RemoteError.serializer().descriptor,
                "LogSnapshot" to LogSnapshot.serializer().descriptor,
            )
            .generate()
}

@OptIn(ExperimentalSerializationApi::class)
private class DescriptorSchemas(vararg roots: Pair<String, SerialDescriptor>) {
    private val roots = roots.toList()
    private val names = roots.associate { (name, descriptor) -> descriptor to name }.toMutableMap()
    private val definitions = sortedMapOf<String, JsonElement>()
    private val owners = mutableMapOf<String, SerialDescriptor>()

    fun generate(): JsonObject {
        roots.forEach { (_, descriptor) -> schema(descriptor) }
        return JsonObject(definitions)
    }

    private fun name(descriptor: SerialDescriptor): String =
        names.getOrPut(descriptor) {
            val simple = descriptor.serialName.removeSuffix("?").substringAfterLast('.')
            when {
                simple == "VersionedValue" -> "Versioned" + name(descriptor.getElementDescriptor(1))
                descriptor.kind == StructureKind.LIST ->
                    "ListOf" + name(descriptor.getElementDescriptor(0))
                else -> simple.replaceFirstChar { it.uppercaseChar() }
            }
        }

    private fun schema(descriptor: SerialDescriptor): JsonObject {
        val result =
            when {
                // This field belongs to the existing MediaSourceConfig model and its factory codec.
                descriptor.serialName.removeSuffix("?") ==
                    "kotlinx.serialization.json.JsonElement" ->
                    obj(
                        "description" to
                            JsonPrimitive("Factory-specific MediaSourceConfig.serializedArguments")
                    )
                descriptor.isInline -> schema(descriptor.getElementDescriptor(0))
                else ->
                    when (descriptor.kind) {
                        PrimitiveKind.STRING,
                        PrimitiveKind.CHAR -> obj("type" to JsonPrimitive("string"))
                        PrimitiveKind.BOOLEAN -> obj("type" to JsonPrimitive("boolean"))
                        PrimitiveKind.BYTE,
                        PrimitiveKind.SHORT,
                        PrimitiveKind.INT ->
                            obj(
                                "type" to JsonPrimitive("integer"),
                                "format" to JsonPrimitive("int32"),
                            )
                        PrimitiveKind.LONG ->
                            obj(
                                "type" to JsonPrimitive("integer"),
                                "format" to JsonPrimitive("int64"),
                            )
                        PrimitiveKind.FLOAT ->
                            obj(
                                "type" to JsonPrimitive("number"),
                                "format" to JsonPrimitive("float"),
                            )
                        PrimitiveKind.DOUBLE ->
                            obj(
                                "type" to JsonPrimitive("number"),
                                "format" to JsonPrimitive("double"),
                            )
                        SerialKind.ENUM ->
                            obj(
                                "type" to JsonPrimitive("string"),
                                "enum" to
                                    JsonArray(
                                        (0 until descriptor.elementsCount).map {
                                            JsonPrimitive(descriptor.getElementName(it))
                                        }
                                    ),
                            )
                        StructureKind.LIST ->
                            obj(
                                "type" to JsonPrimitive("array"),
                                "items" to schema(descriptor.getElementDescriptor(0)),
                            )
                        StructureKind.MAP ->
                            obj(
                                "type" to JsonPrimitive("object"),
                                "additionalProperties" to
                                    schema(descriptor.getElementDescriptor(1)),
                            )
                        StructureKind.CLASS,
                        StructureKind.OBJECT,
                        PolymorphicKind.SEALED -> reference(descriptor)
                        else ->
                            error(
                                "Remote settings requires a concrete or sealed serializer: ${descriptor.serialName}"
                            )
                    }
            }
        return if (descriptor.isNullable)
            obj("allOf" to JsonArray(listOf(result)), "nullable" to JsonPrimitive(true))
        else result
    }

    private fun reference(descriptor: SerialDescriptor): JsonObject {
        val name = name(descriptor)
        val owner = owners.putIfAbsent(name, descriptor)
        check(
            owner == null ||
                owner.serialName.removeSuffix("?") == descriptor.serialName.removeSuffix("?")
        ) {
            "Ambiguous schema name: $name"
        }
        if (name !in definitions) {
            definitions[name] = obj()
            definitions[name] =
                if (descriptor.kind == PolymorphicKind.SEALED) {
                    val variants = descriptor.getElementDescriptor(1)
                    val mapping = linkedMapOf<String, JsonElement>()
                    val refs =
                        (0 until variants.elementsCount).map { index ->
                            val variant = variants.getElementDescriptor(index)
                            val tag = variants.getElementName(index)
                            names[variant] =
                                name +
                                    tag.substringAfterLast('.').replaceFirstChar {
                                        it.uppercaseChar()
                                    }
                            val variantName = name(variant)
                            mapping[tag] = JsonPrimitive("#/components/schemas/$variantName")
                            val ref = reference(variant)
                            val definition = definitions.getValue(variantName).jsonObject
                            val properties = definition.getValue("properties").jsonObject
                            definitions[variantName] =
                                JsonObject(
                                    definition +
                                        mapOf(
                                            "properties" to
                                                JsonObject(
                                                    properties +
                                                        ("type" to
                                                            obj(
                                                                "type" to JsonPrimitive("string"),
                                                                "enum" to
                                                                    JsonArray(
                                                                        listOf(JsonPrimitive(tag))
                                                                    ),
                                                            ))
                                                ),
                                            "required" to
                                                JsonArray(
                                                    listOf(JsonPrimitive("type")) +
                                                        (definition["required"] as? JsonArray)
                                                            .orEmpty()
                                                ),
                                        )
                                )
                            ref
                        }
                    obj(
                        "oneOf" to JsonArray(refs),
                        "discriminator" to
                            obj(
                                "propertyName" to JsonPrimitive("type"),
                                "mapping" to JsonObject(mapping),
                            ),
                    )
                } else {
                    val properties = linkedMapOf<String, JsonElement>()
                    val required = mutableListOf<JsonElement>()
                    for (index in 0 until descriptor.elementsCount) {
                        val property = descriptor.getElementName(index)
                        properties[property] = schema(descriptor.getElementDescriptor(index))
                        if (!descriptor.isElementOptional(index))
                            required += JsonPrimitive(property)
                    }
                    obj(
                        "type" to JsonPrimitive("object"),
                        "properties" to JsonObject(properties),
                        "required" to JsonArray(required),
                        "additionalProperties" to JsonPrimitive(false),
                    )
                }
        }
        return obj("$" + "ref" to JsonPrimitive("#/components/schemas/$name"))
    }

    private fun obj(vararg values: Pair<String, JsonElement>) = JsonObject(mapOf(*values))
}
