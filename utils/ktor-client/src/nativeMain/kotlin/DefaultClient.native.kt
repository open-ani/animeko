/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.bits.storeByteArray
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.charsets.decode
import io.ktor.utils.io.core.Input
import korlibs.io.serialization.xml.Xml
import kotlinx.io.Source
import me.him188.ani.utils.xml.Document

internal actual fun getXmlConverter(): ContentConverter {
    return XmlConverter
}

private object XmlConverter : ContentConverter {
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        if (typeInfo.type.qualifiedName != Document::class.qualifiedName) return null
        content.awaitContent()
        val decoder = Charsets.UTF_8.newDecoder()
        val string = decoder.decode(content.toSource().asKtorInput())
        return Xml.parse(string)
    }

    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent? {
        return null
    }
}

@Suppress("DEPRECATION")
private fun Source.asKtorInput(): Input {
    return object : Input() {
        val buffer = ByteArray(4096)
        override fun closeSource() {
            this@asKtorInput.close()
        }

        override fun fill(destination: Memory, offset: Int, length: Int): Int {
            val read = readAtMostTo(buffer, 0, minOf(buffer.size, length))
            if (read == -1) return -1
            destination.storeByteArray(offset, buffer, 0, read)
            return read
        }
    }
}
