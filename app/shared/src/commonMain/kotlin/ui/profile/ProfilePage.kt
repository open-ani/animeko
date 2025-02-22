/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.settings.tabs.AniHelpSection

@Composable
fun ProfilePage(
    onClickSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val viewModel = remember { AccountViewModel() }

    // user profile
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            /*SelfInfo(
                viewModel.authState,
                viewModel.authState.isKnownLoggedOut || viewModel.authState.isKnownGuest,
                onClickSettings,
                modifier = Modifier
                    .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                    .fillMaxWidth(),
            )*/
        },
    ) { topBarPaddings ->
        // debug
        DebugInfoView(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(
                    top = topBarPaddings.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
        )
    }

}

@Composable
private fun DebugInfoView(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "感谢你的支持",
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )

        AniHelpSection(Modifier)
    }
}


/*
@Composable
internal fun SelfInfo(
    authState: AuthState,
    isLoggedOut: Boolean,
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        UserInfoRow(
            authState.status?.userInfoOrNull,
            onClickEditNickname = {},
            onClickSettings = onClickSettings,
            modifier,
        )

        if (isLoggedOut) {
            Surface(Modifier.align(Alignment.Center)) {
                val navigator = LocalNavigator.current
                Box(Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton({ authState.launchAuthorize(navigator) }) {
                        Text("请先登录")
                    }
                }
            }
        }
    }
}
*/
