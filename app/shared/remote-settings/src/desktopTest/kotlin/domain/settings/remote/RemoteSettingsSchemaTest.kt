/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class RemoteSettingsSchemaTest {
    @Test
    fun checkedInContractMatchesRuntimeSerializers() {
        val contract =
            Json.parseToJsonElement(File("../remote-settings-contract/openapi.json").readText())
                .jsonObject
        assertEquals(
            GenerateRemoteSettingsOpenApi.generateSchemas(),
            contract.getValue("components").jsonObject.getValue("schemas"),
            "Regenerate the remote settings OpenAPI schemas after changing a wire model",
        )
    }
}
