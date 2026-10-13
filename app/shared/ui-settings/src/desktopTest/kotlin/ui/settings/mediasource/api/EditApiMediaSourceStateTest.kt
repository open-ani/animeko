/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.domain.mediasource.api.ApiDetail
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiPlayback
import me.him188.ani.app.domain.mediasource.api.ApiRequest
import me.him188.ani.app.domain.mediasource.api.ApiSearch
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.app.domain.mediasource.subscription.UnsupportedSubscriptionSchemaException
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import me.him188.ani.app.ui.settings.mediasource.rss.createTestSaveableStorage
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditApiMediaSourceStateTest {
    private val codecs = MediaSourceCodecManager()
    private val arguments = ApiMediaSourceArguments(
        name = "Editable API",
        search = ApiSearch(
            request = ApiRequest(url = "https://api.test/search?q={keyword}"),
            subjectUrl = "https://site.test/{subjectId}",
        ),
        detail = ApiDetail(
            request = ApiRequest(url = "https://api.test/detail/{subjectId}"),
            episodeUrl = "https://site.test/{episodeId}",
        ),
        playback = ApiPlayback(request = ApiRequest(url = "https://api.test/play/{episodeId}")),
    )
    private val postArguments = arguments.copy(
        search = arguments.search.copy(
            request = ApiRequest(
                url = "https://api.test/search",
                method = "POST",
                body = buildJsonObject { put("q", JsonPrimitive("{keyword}")) },
            ),
        ),
    )

    private class Fixture(
        val state: EditApiMediaSourceState,
        val storage: SaveableStorage<ApiMediaSourceArguments>,
    ) {
        val saved: ApiMediaSourceArguments get() = assertNotNull(storage.container)
    }

    private fun fixture(
        initial: ApiMediaSourceArguments = arguments,
        allowEdit: Boolean = true,
    ): Fixture {
        val storage = createTestSaveableStorage(initial)
        return Fixture(EditApiMediaSourceState(storage, stateOf(allowEdit), "test", codecs), storage)
    }

    @Test
    fun `plain fields write through to storage`() {
        val fixture = fixture()
        fixture.state.displayName = "Renamed"
        fixture.state.search.itemsPath = "$.list[*]"
        assertEquals("Renamed", fixture.saved.name)
        assertEquals("$.list[*]", fixture.saved.search.itemsPath)
    }

    @Test
    fun `incomplete JSON body stays a draft and is not saved`() {
        val fixture = fixture(postArguments)
        val body = fixture.state.search.request.body
        body.edit("{")
        assertEquals("{", body.text)
        assertTrue(body.isError)
        assertEquals(postArguments.search.request.body, fixture.saved.search.request.body)

        body.edit("""{"q": "{keyword}", "page": "{page}"}""")
        assertFalse(body.isError)
        assertEquals("""{"q":"{keyword}","page":"{page}"}""", fixture.saved.search.request.body.toString())
        // 保存后仍然显示用户输入的原文, 而不是重新格式化的 JSON
        assertEquals("""{"q": "{keyword}", "page": "{page}"}""", body.text)
    }

    @Test
    fun `cleared number stays a draft until it parses`() {
        val fixture = fixture()
        val pageSize = fixture.state.search.pageSize
        pageSize.edit("")
        assertEquals("", pageSize.text)
        assertTrue(pageSize.isError)
        assertEquals(arguments.search.pageSize, fixture.saved.search.pageSize)

        pageSize.edit("25")
        assertFalse(pageSize.isError)
        assertEquals(25, fixture.saved.search.pageSize)
    }

    @Test
    fun `drafts survive edits to other fields`() {
        val fixture = fixture(postArguments)
        val body = fixture.state.search.request.body
        body.edit("{")
        fixture.state.displayName = "Renamed"
        fixture.state.search.pageSize.edit("7")
        assertEquals("{", body.text)
        assertTrue(body.isError)
        assertEquals("Renamed", fixture.saved.name)
        assertEquals(7, fixture.saved.search.pageSize)
        assertEquals(postArguments.search.request.body, fixture.saved.search.request.body)
    }

    @Test
    fun `external changes replace drafts`() {
        val fixture = fixture(postArguments)
        val body = fixture.state.search.request.body
        body.edit("{")
        assertTrue(body.isError)

        val imported = postArguments.copy(
            search = postArguments.search.copy(
                request = postArguments.search.request.copy(body = buildJsonObject { put("id", JsonPrimitive(1)) }),
            ),
        )
        fixture.storage.set(imported)
        assertFalse(body.isError)
        assertEquals(Json.parseToJsonElement(body.text), imported.search.request.body)
    }

    @Test
    fun `switching to GET drops the request body`() {
        val fixture = fixture(postArguments)
        fixture.state.search.request.method = "GET"
        assertEquals("GET", fixture.saved.search.request.method)
        assertNull(fixture.saved.search.request.body)
        assertEquals("", fixture.state.search.request.body.text)
    }

    @Test
    fun `json editor saves parsed configuration and keeps malformed text as draft`() {
        val fixture = fixture()
        val renamed = arguments.copy(name = "Renamed API")
        fixture.state.jsonEditor.edit(codecs.serializeToString(listOf(renamed)))
        assertEquals(renamed, fixture.saved)

        fixture.state.jsonEditor.edit("{")
        assertTrue(fixture.state.jsonEditor.isError)
        assertEquals("{", fixture.state.jsonEditor.text)
        assertEquals(renamed, fixture.saved)
    }

    @Test
    fun `json editor imports current and legacy subscription formats`() {
        val exported = codecs.serializeToString(listOf(arguments))
        val sources = Json.parseToJsonElement(exported).jsonObject.getValue("mediaSources")
        val formats = listOf(
            apiEditorJson.encodeToString(ApiMediaSourceArguments.serializer(), arguments),
            exported,
            """{"mediaSources":$sources}""",
            """{"exportedMediaSourceDataList":{"mediaSources":$sources}}""",
        )
        for (text in formats) {
            val fixture = fixture()
            fixture.state.jsonEditor.edit(text)
            assertFalse(fixture.state.jsonEditor.isError)
            assertEquals(arguments, fixture.saved)
        }
        assertFailsWith<UnsupportedSubscriptionSchemaException> {
            decodeApiArguments("""{"schemaVersion":999,"mediaSources":$sources}""", codecs)
        }
    }

    @Test
    fun `json editor rejects another factory and multiple sources`() {
        assertFailsWith<IllegalArgumentException> {
            decodeApiArguments("""{"mediaSources":[{"factoryId":"web-selector","version":2,"arguments":{}}]}""", codecs)
        }
        assertFailsWith<IllegalArgumentException> {
            decodeApiArguments(codecs.serializeToString(listOf(arguments, arguments)), codecs)
        }
    }

    @Test
    fun `validation error reflects the saved configuration`() {
        assertNotNull(fixture(ApiMediaSourceArguments()).state.validationError)
        val fixture = fixture()
        assertNull(fixture.state.validationError)
        fixture.state.search.subjectUrl = "/relative"
        assertNotNull(fixture.state.validationError)
    }

    @Test
    fun `form fields are disabled when editing is not allowed`() = runAniComposeUiTest {
        val fixture = fixture(allowEdit = false)
        setContent {
            ProvideCompositionLocalsForPreview {
                ApiConfigurationPane(fixture.state)
            }
        }
        onNodeWithTag(ApiConfigurationPaneTestTags.NAME).assertIsNotEnabled()
        onNodeWithTag(ApiConfigurationPaneTestTags.HEADERS).assertIsNotEnabled()
    }

    @Test
    fun `renaming through the form writes to storage`() = runAniComposeUiTest {
        val fixture = fixture()
        setContent {
            ProvideCompositionLocalsForPreview {
                ApiConfigurationPane(fixture.state)
            }
        }
        onNodeWithTag(ApiConfigurationPaneTestTags.NAME).performTextReplacement("Renamed API")
        waitForIdle()
        assertEquals("Renamed API", fixture.saved.name)
    }
}
