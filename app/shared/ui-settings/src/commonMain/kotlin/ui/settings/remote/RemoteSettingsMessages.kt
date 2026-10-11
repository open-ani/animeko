/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.settings.remote

import me.him188.ani.app.domain.settings.remote.RemoteSettingsFailure
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.remote_settings_error_apply_failed
import me.him188.ani.app.ui.lang.remote_settings_error_busy
import me.him188.ani.app.ui.lang.remote_settings_error_connection
import me.him188.ani.app.ui.lang.remote_settings_error_invalid_regex
import me.him188.ani.app.ui.lang.remote_settings_error_invalid_settings
import me.him188.ani.app.ui.lang.remote_settings_error_not_found
import me.him188.ani.app.ui.lang.remote_settings_error_payload_too_large
import me.him188.ani.app.ui.lang.remote_settings_error_plan_expired
import me.him188.ani.app.ui.lang.remote_settings_error_refresh_required
import me.him188.ani.app.ui.lang.remote_settings_error_result_unknown
import me.him188.ani.app.ui.lang.remote_settings_error_revision_conflict
import me.him188.ani.app.ui.lang.remote_settings_error_server_error
import me.him188.ani.app.ui.lang.remote_settings_error_server_restarted
import me.him188.ani.app.ui.lang.remote_settings_error_session_closed
import me.him188.ani.app.ui.lang.remote_settings_error_subscription_refresh_failed
import me.him188.ani.app.ui.lang.remote_settings_error_unauthorized
import me.him188.ani.app.ui.lang.remote_settings_error_unsupported
import me.him188.ani.app.ui.lang.remote_settings_error_version_mismatch
import org.jetbrains.compose.resources.StringResource

internal fun RemoteSettingsFailure.messageResource(): StringResource =
    when (this) {
        RemoteSettingsFailure.CONNECTION -> Lang.remote_settings_error_connection
        RemoteSettingsFailure.UNAUTHORIZED -> Lang.remote_settings_error_unauthorized
        RemoteSettingsFailure.VERSION_MISMATCH -> Lang.remote_settings_error_version_mismatch
        RemoteSettingsFailure.SERVER_RESTARTED -> Lang.remote_settings_error_server_restarted
        RemoteSettingsFailure.SESSION_CLOSED -> Lang.remote_settings_error_session_closed
        RemoteSettingsFailure.REVISION_CONFLICT -> Lang.remote_settings_error_revision_conflict
        RemoteSettingsFailure.REFRESH_REQUIRED -> Lang.remote_settings_error_refresh_required
        RemoteSettingsFailure.RESULT_UNKNOWN -> Lang.remote_settings_error_result_unknown
        RemoteSettingsFailure.BUSY -> Lang.remote_settings_error_busy
        RemoteSettingsFailure.NOT_FOUND -> Lang.remote_settings_error_not_found
        RemoteSettingsFailure.UNSUPPORTED -> Lang.remote_settings_error_unsupported
        RemoteSettingsFailure.SUBSCRIPTION_REFRESH_FAILED ->
            Lang.remote_settings_error_subscription_refresh_failed
        RemoteSettingsFailure.INVALID_SETTINGS -> Lang.remote_settings_error_invalid_settings
        RemoteSettingsFailure.INVALID_REGEX -> Lang.remote_settings_error_invalid_regex
        RemoteSettingsFailure.PLAN_EXPIRED -> Lang.remote_settings_error_plan_expired
        RemoteSettingsFailure.PAYLOAD_TOO_LARGE -> Lang.remote_settings_error_payload_too_large
        RemoteSettingsFailure.APPLY_FAILED -> Lang.remote_settings_error_apply_failed
        RemoteSettingsFailure.SERVER_ERROR -> Lang.remote_settings_error_server_error
    }
