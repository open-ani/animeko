/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */
package me.him188.ani.app.ui.settings.mediasource.api

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import me.him188.ani.app.domain.mediasource.api.ApiDetail
import me.him188.ani.app.domain.mediasource.api.ApiMediaSourceArguments
import me.him188.ani.app.domain.mediasource.api.ApiPlayback
import me.him188.ani.app.domain.mediasource.api.ApiRequest
import me.him188.ani.app.domain.mediasource.api.ApiSearch
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.codec.serializeToString
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ApiConfigurationEditorTest {
    private val codecs = MediaSourceCodecManager()
    private val arguments = ApiMediaSourceArguments(
        name = "Editable API",
        search = ApiSearch(ApiRequest("https://api.test/search?q={keyword}"), subjectUrl = "https://site.test/{subjectId}"),
        detail = ApiDetail(ApiRequest("https://api.test/detail/{subjectId}"), episodeUrl = "https://site.test/{episodeId}"),
        playback = ApiPlayback(ApiRequest("https://api.test/play/{episodeId}")),
    )

    @Test
    fun `editing rejects malformed JSON and saves the imported configuration`() = runAniComposeUiTest {
        var text by mutableStateOf("{}")
        var saved: ApiMediaSourceArguments? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                Column {
                    ApiConfigurationEditor(text, { text = it }, true, codecs, false) { saved = it }
                }
            }
        }
        onNodeWithTag("api-save").assertIsNotEnabled()
        onNodeWithTag("api-error").assertExists()
        onNodeWithTag("api-json").performTextReplacement(codecs.serializeToString(listOf(arguments)))
        onNodeWithTag("api-save").assertIsEnabled().performClick()
        assertEquals(arguments, saved)
        onNodeWithTag("api-json").performTextReplacement("{")
        onNodeWithTag("api-save").assertIsNotEnabled()
        assertEquals(arguments, saved)
    }

    @Test
    fun `subscription settings are read only`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                Column {
                    ApiConfigurationEditor(codecs.serializeToString(listOf(arguments)), {}, false, codecs, false) { error("read only") }
                }
            }
        }
        onNodeWithTag("api-json").assertIsNotEnabled()
        onNodeWithTag("api-save").assertIsNotEnabled()
    }

    @Test
    fun `import rejects another factory and multiple sources`() {
        assertFailsWith<IllegalArgumentException> {
            decodeApiConfiguration("""{"mediaSources":[{"factoryId":"web-selector","version":2,"arguments":{}}]}""", codecs)
        }
        assertFailsWith<IllegalArgumentException> { decodeApiConfiguration(codecs.serializeToString(listOf(arguments, arguments)), codecs) }
    }

    @Test
    fun `form fields edit imported sources without dropping request configuration`() = runAniComposeUiTest {
        val state = ApiConfigurationFormState(codecs)
        state.load(codecs.serializeToString(listOf(arguments)))
        setContent {
            ProvideCompositionLocalsForPreview {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApiConfigurationForm(state, enabled = true, onChange = {})
                }
            }
        }
        onNodeWithTag("api-name").performTextReplacement("Renamed API")
        assertEquals(arguments.copy(name = "Renamed API"), state.configuration())
    }

    @Test
    fun `form subscription fields are disabled`() = runAniComposeUiTest {
        val state = ApiConfigurationFormState(codecs)
        state.load(codecs.serializeToString(listOf(arguments)))
        setContent {
            ProvideCompositionLocalsForPreview {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApiConfigurationForm(state, enabled = false, onChange = {})
                }
            }
        }
        onNodeWithTag("api-name").assertIsNotEnabled()
        onNodeWithTag("api-headers").assertIsNotEnabled()
    }

    @Test
    fun `incomplete JSON and number edits survive other form changes`() {
        val state = ApiConfigurationFormState(codecs)
        val initial = arguments.copy(search = arguments.search.copy(request = arguments.search.request.copy(method = "POST")))
        state.load(codecs.serializeToString(listOf(initial)))
        state.editField("search.body", "{")
        state.edit { it.copy(name = "Draft") }
        assertFailsWith<IllegalArgumentException> { state.configuration() }
        assertEquals("{", state.fields["search.body"])
        state.editField("search.body", """{"q":"{keyword}"}""")
        state.editField("pageSize", "")
        assertFailsWith<IllegalStateException> { state.configuration() }
        state.editField("pageSize", "25")
        val actual = state.configuration()
        assertEquals("Draft", actual.name)
        assertEquals(25, actual.search.pageSize)
        assertEquals(initial.detail, actual.detail)
        assertEquals(initial.playback, actual.playback)
        state.edit { it.copy(search = it.search.copy(request = it.search.request.copy(method = "GET"))) }
        assertNull(state.configuration().search.request.body)
        state.edit { it.copy(search = it.search.copy(request = it.search.request.copy(method = "POST"))) }
        assertEquals("{\"q\":\"{keyword}\"}", state.configuration().search.request.body.toString())
    }
}
