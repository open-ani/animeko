/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.shared

/** License metadata bundled with the application, including native playback dependencies. */
suspend fun loadOpenSourceLibrariesJsons(): List<ByteArray> = listOf(
    Res.readBytes("files/aboutlibraries.json"),
    Res.readBytes("files/additional_libraries.json"),
)
