/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(TestOnly::class)

package me.him188.ani.app.ui.subject.collection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly

@Composable
@PreviewScreenSizes
private fun PreviewCollectionPage() = ProvideCompositionLocalsForPreview {
    val scope = rememberCoroutineScope()
//    CollectionPage(
//        remember {
//            UserCollectionsState(
//                startSearch = {
//                    MutableStateFlow(PagingData.from(TestSubjectCollections))
//                },
//                createTestAuthState(scope),
//                stateOf(TestUserInfo),
//                episodeListStateFactory = 
//            )
//        },
//        WindowInsets(0.dp), {}, {},
//    )
}
