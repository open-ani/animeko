/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class LocationTimeZoneUiStateTest {
    @Test
    fun `success carries zone id`() {
        val state = LocationTimeZoneResult.Success("Asia/Shanghai").toUiState()

        assertEquals(LocationTimeZoneUiState.Success("Asia/Shanghai"), state)
    }

    @Test
    fun `permission denied is retryable`() {
        val state = LocationTimeZoneResult.PermissionDenied.toUiState()

        assertEquals(LocationTimeZoneUiState.Failure(LocationFailureKind.PermissionDenied, canRetry = true), state)
    }

    @Test
    fun `no provider is not retryable`() {
        val state = LocationTimeZoneResult.Failed(LocationFailureReason.NoProvider).toUiState()

        assertEquals(LocationTimeZoneUiState.Failure(LocationFailureKind.LocateFailed, canRetry = false), state)
    }

    @Test
    fun `timeout is retryable`() {
        val state = LocationTimeZoneResult.Failed(LocationFailureReason.Timeout).toUiState()

        assertEquals(LocationTimeZoneUiState.Failure(LocationFailureKind.LocateFailed, canRetry = true), state)
    }

    @Test
    fun `system error is retryable`() {
        val state = LocationTimeZoneResult.Failed(LocationFailureReason.SystemError).toUiState()

        assertEquals(LocationTimeZoneUiState.Failure(LocationFailureKind.LocateFailed, canRetry = true), state)
    }

    @Test
    fun `unsupported platform is not retryable`() {
        val state = LocationTimeZoneResult.UnsupportedPlatform.toUiState()

        assertEquals(LocationTimeZoneUiState.Failure(LocationFailureKind.UnsupportedPlatform, canRetry = false), state)
    }

    @Test
    fun `zone not found is not retryable`() {
        val state = LocationTimeZoneResult.ZoneNotFound.toUiState()

        assertEquals(LocationTimeZoneUiState.Failure(LocationFailureKind.ZoneNotFound, canRetry = false), state)
    }
}
