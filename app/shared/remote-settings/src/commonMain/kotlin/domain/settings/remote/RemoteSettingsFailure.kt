/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.domain.settings.remote

import kotlinx.serialization.SerializationException

/** Presentation-independent failures. Server diagnostics are never used as UI text. */
enum class RemoteSettingsFailure {
    CONNECTION,
    UNAUTHORIZED,
    VERSION_MISMATCH,
    SERVER_RESTARTED,
    SESSION_CLOSED,
    REVISION_CONFLICT,
    REFRESH_REQUIRED,
    RESULT_UNKNOWN,
    BUSY,
    NOT_FOUND,
    UNSUPPORTED,
    SUBSCRIPTION_REFRESH_FAILED,
    INVALID_SETTINGS,
    INVALID_REGEX,
    PLAN_EXPIRED,
    PAYLOAD_TOO_LARGE,
    APPLY_FAILED,
    SERVER_ERROR;

    companion object {
        fun from(error: Throwable): RemoteSettingsFailure =
            when (error) {
                is SerializationException -> INVALID_SETTINGS
                is RemoteSettingsException ->
                    when (error.code) {
                        "UNAUTHORIZED",
                        "FORBIDDEN" -> UNAUTHORIZED
                        "VERSION_MISMATCH" -> VERSION_MISMATCH
                        "SERVER_RESTARTED" -> SERVER_RESTARTED
                        "SESSION_CLOSED" -> SESSION_CLOSED
                        "REVISION_CONFLICT" -> REVISION_CONFLICT
                        "REFRESH_REQUIRED" -> REFRESH_REQUIRED
                        "RESULT_UNKNOWN",
                        "OPERATION_UNKNOWN",
                        "INTERRUPTED" -> RESULT_UNKNOWN
                        "BUSY" -> BUSY
                        "NOT_FOUND" -> NOT_FOUND
                        "UNSUPPORTED_PREFERENCE",
                        "UNSUPPORTED_FACTORY",
                        "UNSUPPORTED_EXPORT" -> UNSUPPORTED
                        "SUBSCRIPTION_REFRESH_FAILED" -> SUBSCRIPTION_REFRESH_FAILED
                        "INVALID_ARGUMENT",
                        "INVALID_CONTENT_TYPE",
                        "INVALID_OPERATION_ID",
                        "OPERATION_ID_REUSED" -> INVALID_SETTINGS
                        "INVALID_REGEX" -> INVALID_REGEX
                        "PLAN_EXPIRED" -> PLAN_EXPIRED
                        "PAYLOAD_TOO_LARGE" -> PAYLOAD_TOO_LARGE
                        "APPLY_FAILED",
                        "DEPENDENCY_FAILED" -> APPLY_FAILED
                        else -> SERVER_ERROR
                    }
                else -> CONNECTION
            }
    }
}
