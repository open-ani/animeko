/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.settings.mediasource.selector.test

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import io.ktor.http.Url
import kotlinx.io.IOException
import me.him188.ani.app.domain.mediasource.test.web.SelectorMediaSourceTester
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSourceEngine
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.WebSearchSubjectInfo
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Element

@Composable
@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Preview
fun PreviewSelectorTestPane() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
    SharedTransitionScope { modifier ->
        @Suppress("AnimatedContentLabel")
        AnimatedContent(1) { _ ->
            Surface {
                SelectorTestPane(
                    remember {
                        SelectorTestState(
                            searchConfigState = mutableStateOf(SelectorSearchConfig.Empty),
                            tester = SelectorMediaSourceTester(TestSelectorMediaSourceEngine()),
                            backgroundScope = scope,
                        ).apply {
                            restartCurrentSubjectSearch()
                        }
                    },
                    {},
                    this,
                    modifier = modifier,
                )
            }
        }
    }
}

@TestOnly
class TestSelectorMediaSourceEngine : SelectorMediaSourceEngine() {
    override suspend fun searchImpl(
        finalUrl: Url
    ): SearchSubjectResult {
        return SearchSubjectResult(
            Url("https://example.com"),
            null,
        )
    }

    override fun selectSubjects(document: Element, config: SelectorSearchConfig): List<WebSearchSubjectInfo> {
        return listOf(
            WebSearchSubjectInfo("a", "Test Subject", "https://example.com/1.html", "1.html", null),
            WebSearchSubjectInfo("a", "Test Subject", "https://example.com/2.html", "2.html", null),
            WebSearchSubjectInfo("a", "Test Subject", "https://example.com/3.html", "3.html", null),
            WebSearchSubjectInfo("a", "Test Subject", "https://example.com/4.html", "4.html", null),
        )
    }

    override suspend fun doHttpGet(uri: String): Document {
        throw IOException("Dummy")
    }
}
