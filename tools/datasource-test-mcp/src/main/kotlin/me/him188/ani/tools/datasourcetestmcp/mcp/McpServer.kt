/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
)

class McpToolRegistration(
    val tool: McpTool,
    val handler: suspend (arguments: JsonElement) -> JsonElement,
)

/**
 * 极简 stdio MCP server.
 *
 * 帧格式遵循 MCP 规范: 每条消息一行 JSON, 以 `\n` 分隔.
 * 同时自动兼容 LSP 式 `Content-Length` 头 (旧版客户端), 按客户端使用的格式回复.
 */
class StdioMcpServer(
    input: InputStream,
    private val output: OutputStream,
    private val registrations: List<McpToolRegistration>,
    private val json: Json,
) {
    private val input = BufferedInputStream(input)

    /** 顶层协议消息必须单行, 不能用 prettyPrint 的 [json] 编码 */
    private val protocolJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private enum class Framing { NEWLINE, CONTENT_LENGTH }

    private var framing = Framing.NEWLINE

    fun run() = runBlocking {
        while (true) {
            val payload = readMessage() ?: break
            if (payload.isBlank()) continue
            val request = runCatching { protocolJson.decodeFromString<RpcRequest>(payload) }.getOrElse { continue }
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
                        protocolJson.encodeToJsonElement(
                            ListSerializer(McpTool.serializer()),
                            registrations.map { it.tool },
                        ),
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
        val registration = registrations.find { it.tool.name == name } ?: run {
            respondError(request.id, -32602, "Unknown tool: $name")
            return
        }
        val arguments = params["arguments"] ?: JsonObject(emptyMap())

        val structured = runCatching { registration.handler(arguments) }.getOrElse { exception ->
            val errorResult = buildJsonObject {
                put("ok", false)
                put("summary", "${exception::class.simpleName}: ${exception.message.orEmpty()}")
            }
            respond(request.id, toolCallResult(errorResult, isError = true))
            return
        }
        respond(request.id, toolCallResult(structured, isError = false))
    }

    private fun toolCallResult(structured: JsonElement, isError: Boolean): JsonObject {
        return buildJsonObject {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", json.encodeToString(JsonElement.serializer(), structured))
                        },
                    )
                },
            )
            put("structuredContent", structured)
            put("isError", isError)
        }
    }

    private fun initializeResult(): JsonObject {
        return buildJsonObject {
            put("protocolVersion", "2024-11-05")
            put(
                "capabilities",
                buildJsonObject {
                    put("tools", buildJsonObject {})
                },
            )
            put(
                "serverInfo",
                buildJsonObject {
                    put("name", "animeko-datasource-test-mcp")
                    put("version", "0.2.0")
                },
            )
        }
    }

    private fun respond(id: JsonElement?, result: JsonElement) {
        if (id == null) return
        writeMessage(protocolJson.encodeToString(RpcResponse(id = id, result = result)))
    }

    private fun respondError(id: JsonElement?, code: Int, message: String) {
        if (id == null) return
        writeMessage(protocolJson.encodeToString(RpcResponse(id = id, error = RpcError(code, message))))
    }

    // region framing

    private fun readMessage(): String? {
        val first = peekNonWhitespace() ?: return null
        return if (first == '{'.code) {
            framing = Framing.NEWLINE
            readLine()
        } else {
            framing = Framing.CONTENT_LENGTH
            readContentLengthMessage()
        }
    }

    private fun peekNonWhitespace(): Int? {
        while (true) {
            input.mark(1)
            val byte = input.read()
            if (byte == -1) return null
            if (!byte.toChar().isWhitespace()) {
                input.reset()
                return byte
            }
        }
    }

    private fun readLine(): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val read = input.read()
            if (read == -1) {
                return if (bytes.isEmpty()) null else bytes.toByteArray().decodeToString()
            }
            if (read == '\n'.code) {
                return bytes.toByteArray().decodeToString().removeSuffix("\r")
            }
            bytes += read.toByte()
        }
    }

    private fun readContentLengthMessage(): String? {
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readLine() ?: return null
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

    private fun writeMessage(body: String) {
        val bytes = body.encodeToByteArray()
        when (framing) {
            Framing.NEWLINE -> {
                output.write(bytes)
                output.write('\n'.code)
            }

            Framing.CONTENT_LENGTH -> {
                output.write("Content-Length: ${bytes.size}\r\n\r\n".encodeToByteArray())
                output.write(bytes)
            }
        }
        output.flush()
    }

    // endregion
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
