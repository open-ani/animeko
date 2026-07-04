/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

class StdioMcpServer(
    private val input: InputStream,
    private val output: OutputStream,
    private val service: SourceTestService,
    private val json: Json,
) {
    private val tools: List<McpTool> = buildTools()

    fun run() = runBlocking {
        val bufferedInput = BufferedInputStream(input)
        while (true) {
            val payload = readMessage(bufferedInput) ?: break
            val request = runCatching { json.decodeFromString<RpcRequest>(payload) }.getOrElse { continue }
            handleRequest(request)
        }
    }

    private suspend fun handleRequest(request: RpcRequest) {
        when (request.method) {
            "initialize" -> respond(request.id, initializeResult())
            "notifications/initialized" -> Unit
            "ping" -> respond(request.id, buildJsonObject {})
            "tools/list" -> respond(
                request.id,
                buildJsonObject {
                    put(
                        "tools",
                        json.encodeToJsonElement(ListSerializer(McpTool.serializer()), tools),
                    )
                },
            )

            "tools/call" -> handleToolsCall(request)
            else -> {
                if (request.id != null) {
                    respondError(request.id, -32601, "Method not found: ${request.method}")
                }
            }
        }
    }

    private suspend fun handleToolsCall(request: RpcRequest) {
        val params = request.params?.jsonObject ?: JsonObject(emptyMap())
        val name = params["name"]?.let { (it as? JsonPrimitive)?.content } ?: run {
            respondError(request.id, -32602, "Missing tool name")
            return
        }
        val arguments = params["arguments"] ?: JsonObject(emptyMap())

        val result = runCatching {
            when (name) {
                "test_subject_episode_source" -> {
                    service.testSubjectEpisodeSource(json.decodeFromJsonElement(arguments))
                }

                "test_resource_page_url" -> {
                    service.testResourcePageUrl(json.decodeFromJsonElement(arguments))
                }

                "probe_video_url" -> {
                    service.probeVideoUrl(json.decodeFromJsonElement(arguments))
                }

                else -> error("Unknown tool: $name")
            }
        }.getOrElse { exception ->
            val errorResult = buildJsonObject {
                put("ok", false)
                put("summary", exception.message.orEmpty())
            }
            respond(
                request.id,
                buildJsonObject {
                    put("content", buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", json.encodeToString(errorResult))
                            },
                        )
                    })
                    put("structuredContent", errorResult)
                    put("isError", true)
                },
            )
            return
        }

        val structured = json.encodeToJsonElement(SourceTestResult.serializer(), result)
        respond(
            request.id,
            buildJsonObject {
                put("content", buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", json.encodeToString(structured))
                        },
                    )
                })
                put("structuredContent", structured)
                put("isError", false)
            },
        )
    }

    private fun initializeResult(): JsonObject {
        return buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject {})
            })
            put("serverInfo", buildJsonObject {
                put("name", "animeko-datasource-test-mcp")
                put("version", "0.1.0")
            })
        }
    }

    private fun respond(id: JsonElement?, result: JsonElement) {
        if (id == null) return
        writeMessage(
            json.encodeToString(
                RpcResponse(
                    id = id,
                    result = result,
                ),
            ),
        )
    }

    private fun respondError(id: JsonElement?, code: Int, message: String) {
        if (id == null) return
        writeMessage(
            json.encodeToString(
                RpcResponse(
                    id = id,
                    error = RpcError(code, message),
                ),
            ),
        )
    }

    private fun readMessage(input: InputStream): String? {
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readHeaderLine(input) ?: return null
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: return null
        val body = input.readNBytes(contentLength)
        if (body.size != contentLength) return null
        return body.decodeToString()
    }

    private fun readHeaderLine(input: InputStream): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val read = input.read()
            if (read == -1) {
                return if (bytes.isEmpty()) null else bytes.toByteArray().decodeToString().trimEnd('\r')
            }
            bytes += read.toByte()
            val size = bytes.size
            if (size >= 2 && bytes[size - 2] == '\r'.code.toByte() && bytes[size - 1] == '\n'.code.toByte()) {
                return bytes.toByteArray().decodeToString().removeSuffix("\r\n")
            }
        }
    }

    private fun writeMessage(body: String) {
        val bytes = body.encodeToByteArray()
        output.write("Content-Length: ${bytes.size}\r\n\r\n".encodeToByteArray())
        output.write(bytes)
        output.flush()
    }

    private fun buildTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "test_subject_episode_source",
                description = "Fetch Ani subject/episode metadata, query a datasource locally, resolve a final video URL, and probe playback reachability.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("required", buildJsonArray {
                        add(JsonPrimitive("subjectId"))
                        add(JsonPrimitive("episodeId"))
                    })
                    put("properties", buildJsonObject {
                        put("subjectId", integerSchema("Ani subject ID"))
                        put("episodeId", integerSchema("Ani episode ID"))
                        put("aniApiBaseUrl", stringSchema("Ani API base URL"))
                        put("aniBearerToken", stringSchema("Optional Ani bearer token"))
                        put("maxCandidates", integerSchema("Max media candidates to collect"))
                        put("fetchTimeoutMillis", integerSchema("Fetch timeout in milliseconds"))
                        put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds"))
                        put("candidateTestMode", buildJsonObject {
                            put("type", "string")
                            put("description", JsonPrimitive("Candidate testing mode. Defaults to all_channels."))
                            put("enum", buildJsonArray {
                                add(JsonPrimitive("all_channels"))
                                add(JsonPrimitive("first_success"))
                            })
                        })
                        put("mediaSource", mediaSourceSchema())
                    })
                },
            ),
            McpTool(
                name = "test_resource_page_url",
                description = "Resolve a final video URL from a resource or playback page and probe playback reachability.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("required", buildJsonArray { add(JsonPrimitive("pageUrl")) })
                    put("properties", buildJsonObject {
                        put("pageUrl", stringSchema("Resource page or playback page URL"))
                        put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds"))
                        put("resolveDepth", integerSchema("Max nested page traversal depth"))
                        put("mediaSource", mediaSourceSchema())
                    })
                },
            ),
            McpTool(
                name = "probe_video_url",
                description = "Probe a final video URL such as m3u8 or mp4 and report whether it is reachable.",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    put("required", buildJsonArray { add(JsonPrimitive("videoUrl")) })
                    put("properties", buildJsonObject {
                        put("videoUrl", stringSchema("Final video URL"))
                        put("probeTimeoutMillis", integerSchema("Probe timeout in milliseconds"))
                        put("headers", buildJsonObject {
                            put("type", "object")
                            put("description", JsonPrimitive("Optional request headers"))
                        })
                    })
                },
            ),
        )
    }

    private fun stringSchema(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", JsonPrimitive(description))
    }

    private fun integerSchema(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", JsonPrimitive(description))
    }

    private fun mediaSourceSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        put("required", buildJsonArray { add(JsonPrimitive("factoryId")) })
        put("properties", buildJsonObject {
            put("factoryId", stringSchema("Datasource factory ID"))
            put("mediaSourceId", stringSchema("Datasource instance ID"))
            put("serializedArguments", buildJsonObject {
                put("description", JsonPrimitive("Datasource serialized arguments JSON"))
            })
            put("arguments", buildJsonObject {
                put("type", "object")
                put("description", JsonPrimitive("Legacy string arguments for datasource factories"))
            })
        })
    }
}

@Serializable
private data class RpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
private data class RpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement,
    val result: JsonElement? = null,
    val error: RpcError? = null,
)

@Serializable
private data class RpcError(
    val code: Int,
    val message: String,
)
