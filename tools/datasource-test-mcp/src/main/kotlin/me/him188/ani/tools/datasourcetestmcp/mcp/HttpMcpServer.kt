/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.mcp

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull

/**
 * 极简 MCP Streamable HTTP server, 实现规范的无状态子集:
 *
 * - `POST /mcp` (或 `/`) 接收单条 JSON-RPC 消息; 请求返回 `application/json`, notification 返回 202.
 * - 不支持 SSE 流与会话: `GET`/`DELETE` 返回 405. 对 Claude Code 等以 JSON 响应工作的客户端足够.
 * - 校验 `Origin` 防 DNS rebinding (规范要求), 仅接受本机 Origin 或无 Origin 的请求.
 */
fun runHttpMcpServer(host: String, port: Int, handler: McpRequestHandler) {
    embeddedServer(CIO, host = host, port = port) { mcpServerModule(handler) }
        .start(wait = true)
}

fun Application.mcpServerModule(handler: McpRequestHandler) {
    routing {
        post("/mcp") { handleMcpPost(handler) }
        post("/") { handleMcpPost(handler) }
        get("/mcp") { respondMethodNotAllowed() }
        get("/") { respondMethodNotAllowed() }
        delete("/mcp") { respondMethodNotAllowed() }
    }
}

private suspend fun RoutingContext.handleMcpPost(handler: McpRequestHandler) {
    val protocolJson = handler.protocolJson

    val origin = call.request.headers[HttpHeaders.Origin]
    if (!isLocalOrigin(origin)) {
        call.respond(HttpStatusCode.Forbidden)
        return
    }

    val element = runCatching { protocolJson.parseToJsonElement(call.receiveText()) }.getOrNull()
        ?: return respondRpcError(protocolJson, -32700, "Parse error")
    if (element is JsonArray) {
        return respondRpcError(protocolJson, -32600, "Batch requests are not supported")
    }
    val request = runCatching { protocolJson.decodeFromJsonElement(RpcRequest.serializer(), element) }.getOrNull()
        ?: return respondRpcError(protocolJson, -32600, "Invalid request")

    val response = handler.handle(request)
    if (response == null) {
        call.respond(HttpStatusCode.Accepted)
    } else {
        call.respondText(
            protocolJson.encodeToString(RpcResponse.serializer(), response),
            ContentType.Application.Json,
        )
    }
}

private suspend fun RoutingContext.respondRpcError(protocolJson: Json, code: Int, message: String) {
    call.respondText(
        protocolJson.encodeToString(
            RpcResponse.serializer(),
            RpcResponse(id = JsonNull, error = RpcError(code, message)),
        ),
        ContentType.Application.Json,
        HttpStatusCode.BadRequest,
    )
}

private suspend fun RoutingContext.respondMethodNotAllowed() {
    call.response.headers.append(HttpHeaders.Allow, "POST")
    call.respond(HttpStatusCode.MethodNotAllowed)
}

private fun isLocalOrigin(origin: String?): Boolean {
    if (origin.isNullOrEmpty()) return true
    val host = runCatching { Url(origin).host }.getOrNull() ?: return false
    return host.equals("localhost", ignoreCase = true) ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1"
}
