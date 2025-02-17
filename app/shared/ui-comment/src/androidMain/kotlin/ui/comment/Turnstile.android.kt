/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.comment

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout.LayoutParams
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import me.him188.ani.app.domain.comment.TurnstileState
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import java.io.InputStream

class AndroidTurnstileState(
    override val url: String,
) : TurnstileState {
    private val webViewState = mutableStateOf<WebView?>(null)
    var webView: WebView?
        get() = webViewState.value
        set(value) {
            if (webViewState.value == value) return
            webViewState.value = value
            value?.applySettings()
            value?.restoreOrLoadPage()
        }

    var isDarkTheme: Boolean = false

    // WebView 重新创建的时候会使用此 state bundle 恢复状态
    private var webViewStateBundle: Bundle? = null

    private val tokenChannel = Channel<String>()
    private val webErrorChannel = Channel<TurnstileState.Error>()

    override val tokenFlow: Flow<String>
        get() = tokenChannel.receiveAsFlow()
    override val webErrorFlow: Flow<TurnstileState.Error>
        get() = webErrorChannel.receiveAsFlow()
    
    
    private fun concatUrl(): String {
        return "${url}&theme=${if (isDarkTheme) "dark" else "light"}"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun WebView.applySettings() {
        settings.apply {
            setSupportZoom(false)
            displayZoomControls = false
            builtInZoomControls = false
            javaScriptEnabled = true
            domStorageEnabled = true
            setBackgroundColor(Color.TRANSPARENT)
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request?.url != null) {
                    val requestUrl = request.url.toString()
                    if (requestUrl.startsWith(TurnstileState.CALLBACK_INTERCEPTION_PREFIX)) {
                        logger.info { "Intercepted request: $requestUrl" }
                        val responseToken = TurnstileState.CALLBACK_REGEX
                            .matchEntire(requestUrl)?.groupValues?.getOrNull(1)
                        if (responseToken != null) {
                            tokenChannel.trySend(responseToken)
                            return WebResourceResponse("text/plain", "utf-8", createEmptyInputStream())
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame != true || error == null) return super.onReceivedError(view, request, error)
                
                if (error.errorCode in networkErrors) {
                    webErrorChannel.trySend(TurnstileState.Error.Network(error.errorCode))
                } else {
                    webErrorChannel.trySend(TurnstileState.Error.Unknown(error.errorCode))
                }
                return super.onReceivedError(view, request, error)
            }
        }
    }

    private fun WebView.reloadPage() {
        loadUrl(concatUrl())
    }

    private fun WebView.restoreOrLoadPage() {
        val currentState = webViewStateBundle
        if (currentState != null) {
            restoreState(currentState)
            return
        }
        reloadPage()
    }

    override fun reload() {
        webView?.reloadPage()
    }

    override fun cancel() {
        webView?.destroy()
        webView = null
    }

    private fun createEmptyInputStream(): InputStream {
        return object : InputStream() {
            override fun read(): Int = -1
        }
    }

    private companion object {
        private val logger = logger<AndroidTurnstileState>()
        private val networkErrors = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_IO,
            WebViewClient.ERROR_TIMEOUT,
        )
    }
}

actual fun createTurnstileState(url: String): TurnstileState {
    return AndroidTurnstileState(url)
}

@Composable
actual fun ActualTurnstile(
    state: TurnstileState,
    constraints: Constraints,
    modifier: Modifier,
) {
    check(state is AndroidTurnstileState)
    val isDark = isSystemInDarkTheme()
    val currentLayoutParams = remember(constraints) {
        LayoutParams(
            if (constraints.hasFixedWidth) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT,
            if (constraints.hasFixedHeight) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT,
        )
    }

    val context = LocalContext.current
    val webView = remember { WebView(context) }

    AndroidView(
        factory = { _ ->
            webView
                .apply { layoutParams = currentLayoutParams }
                .also {
                    state.isDarkTheme = isDark
                    state.webView = it
                }
        },
        modifier = modifier,
        update = { wv ->
            wv.layoutParams = currentLayoutParams
            state.isDarkTheme = isDark
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            webView.removeAllViews()
        }
    }
}

/**
 * This preview is only available when running at real device.
 */
@Preview
@Composable
fun PreviewBangumi38DevTurnstile() {
    ProvideCompositionLocalsForPreview {
        val context = LocalContext.current
        CompositionLocalProvider(
            LocalToaster provides object : Toaster {
                override fun toast(text: String) {
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                }
            },
        ) {
            val toaster = LocalToaster.current
            val state = remember { TurnstileState(url = "") }

            LaunchedEffect(Unit) {
                state.tokenFlow.collectLatest {
                    toaster.toast("get token: $it")
                }
            }

            Column {
                BoxWithConstraints {
                    ActualTurnstile(
                        state = state,
                        constraints = constraints,
                        modifier = Modifier,
                    )
                }
                Row {
                    Button({ state.reload() }) { Text("Reload") }
                }
            }
        }
    }
}