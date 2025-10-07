/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.feedback

import androidx.annotation.UiThread
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.loading.ConnectingDialog
import kotlin.time.Duration.Companion.seconds


/**
 * A controller for a dialog that shows an error message and a cancel button.
 *
 * @see ErrorDialogHost
 */
interface ErrorDialogController {
    val isVisible: Boolean
        @Composable get

    fun hide()

    fun show()

    val debugInfo: String?
        @Composable get

    fun setDebugInfo(debugInfo: String?)
}

class StateErrorDialogController : ErrorDialogController {
    private var _isVisible: Boolean by mutableStateOf(false)

    override val isVisible: Boolean
        @Composable get() = _isVisible

    override fun hide() {
        _isVisible = false
    }

    override fun show() {
        _isVisible = true
    }

    private var _debugInfo: String? by mutableStateOf("")
    override val debugInfo: String?
        @Composable get() = _debugInfo


    override fun setDebugInfo(debugInfo: String?) {
        _debugInfo = debugInfo
    }
}

/**
 * Composes a [ErrorDialogHost] that shows a "Connection lost, reconnecting..." message and a cancel button.
 *
 * [errorFlow] is a flow of error messages that will trigger the dialog to show.
 * If the flow emits `null`, the dialog is hidden.
 *
 * @param onClickCancel the action to perform when the cancel button is clicked, when the error is [recovering][ErrorMessage.isRecovering].
 * @param onConfirm the action to perform when the confirm button is clicked, when the error is not [recovering][ErrorMessage.isRecovering].
 */
@Composable
fun ErrorDialogHost(
    errorFlow: MutableStateFlow<ErrorMessage?>,
    onClickCancel: () -> Unit = {},
    onConfirm: () -> Unit = {
        errorFlow.value = null
    },
) {
    return ErrorDialogHost(
        errorFlow = errorFlow as Flow<ErrorMessage?>,
        onClickCancel = onClickCancel,
        onConfirm = onConfirm,
    )
}

/**
 * Composes a [ErrorDialogHost] that shows a "Connection lost, reconnecting..." message and a cancel button.
 *
 * [errorFlow] is a flow of error messages that will trigger the dialog to show.
 * If the flow emits `null`, the dialog is hidden.
 *
 * @param onClickCancel the action to perform when the cancel button is clicked, when the error is [recovering][ErrorMessage.isRecovering].
 * @param onConfirm the action to perform when the confirm button is clicked, when the error is not [recovering][ErrorMessage.isRecovering].
 */
@Composable
fun ErrorDialogHost(
    errorFlow: Flow<ErrorMessage?>,
    onClickCancel: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    val controller = remember {
        StateErrorDialogController()
    }

    val error = remember(errorFlow) {
        errorFlow.distinctUntilChanged()
            .debounce(0.5.seconds)
    }.collectAsStateWithLifecycle(null).value

    LaunchedEffect(error) {
        if (error != null) {
            controller.show()
        } else {
            controller.hide()
        }
    }

    if (controller.isVisible) {
        ConnectingDialog(
            text = {
                Text(text = error?.message ?: "Operation failed, please try again")
                val cause = error?.cause
                if (cause != null) {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(text = cause.stackTraceToString())
                    }
                }
            },
            progress = if (error?.isRecovering == true) {
                {
                    LinearProgressIndicator(Modifier.width(128.dp))
                }
            } else null,
            onDismissRequest = {
                if (error?.isRecovering == false) {
                    controller.hide()
                }
            },
            confirmButton = {
                if (error?.isRecovering == false) {
                    if (error.cause != null) {
                        val clipboard = LocalClipboard.current
                        val scope = rememberCoroutineScope()
                        TextButton(
                            onClick = {
                                val copyTarget = "删除缓存失败\n\n" + error.cause?.stackTraceToString()
                                scope.launch {
                                    clipboard.setClipEntryText(copyTarget)
                                }
                            },
                        ) {
                            Text("复制")
                        }
                    }
                    TextButton(
                        onClick = {
                            controller.hide()
                            error.onConfirm?.invoke()
                            onConfirm()
                        },
                    ) {
                        Text("OK")
                    }
                } else {
                    // recovering
                    TextButton(
                        onClick = {
                            controller.hide()
                            error?.onCancel?.invoke()
                            onClickCancel()
                        },
                    ) {
                        Text("取消")
                    }
                }
            },
        )
    }
}


///**
// * Shows a [Toast] with the error message when [error] emits an error message.
// */
//@Composable
//fun ErrorToast(
//    error: Flow<ErrorMessage?>,
//) {
//    val context = LocalContext.current
//    LaunchedEffect(error) {
//        error.distinctUntilChanged()
//            .debounce(0.5.seconds)
//            .collect { error ->
//                if (error != null) {
//                    withContext(Dispatchers.Main.immediate) {
//                        Toast.makeText(
//                            context,
//                            error.message ?: "Operation failed, please try again",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//                }
//            }
//    }
//}

/**
 * An error message to be presented.
 */
@Stable
interface ErrorMessage {
    val message: String?
    val cause: Throwable?

    val isRecovering: Boolean

    val onConfirm: (() -> Unit)?
    val onCancel: (() -> Unit)?

    companion object Factory {
        /**
         * A network error that automatically recovering.
         *
         * @param cause internal cause of the error, to be displayed when the app is built in debug mode
         */
        fun networkErrorRecovering(cause: Throwable? = null): ErrorMessage =
            SimpleErrorMessage("Connection lost, reconnecting...", cause, isRecovering = true)

        /**
         * A network error that is not automatically recovering.
         *
         * Users will see a simple alert dialog with the message and a "OK" button.
         *
         * @param cause internal cause of the error, to be displayed when the app is built in debug mode
         */
        fun networkError(cause: Throwable? = null): ErrorMessage =
            SimpleErrorMessage("Network error, please check your connection and try again", cause)

        fun simple(
            message: String?,
            cause: Throwable? = null,
            @UiThread onConfirm: (() -> Unit)? = null
        ): ErrorMessage =
            SimpleErrorMessage(message, cause, onConfirm = onConfirm)

        fun processing(
            message: String?,
            cause: Throwable? = null,
            @UiThread onCancel: (() -> Unit)? = null
        ): ErrorMessage =
            SimpleErrorMessage(message, cause, isRecovering = true, onCancel = onCancel)
    }

    private class SimpleErrorMessage(
        override val message: String?,
        override val cause: Throwable? = null,
        override val isRecovering: Boolean = false,
        override val onConfirm: (() -> Unit)? = null,
        override val onCancel: (() -> Unit)? = null,
    ) : ErrorMessage
}
