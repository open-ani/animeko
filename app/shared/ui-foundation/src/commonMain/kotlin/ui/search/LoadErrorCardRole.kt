/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.search

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import me.him188.ani.app.domain.foundation.LoadError

/**
 * @see LoadErrorCardRole.from
 * @see LoadError
 */
@Stable
sealed class LoadErrorCardRole {
    @Composable
    internal abstract fun Container(
        modifier: Modifier = Modifier,
        containerColor: Color = LoadErrorDefaults.containerColor,
        shape: Shape = MaterialTheme.shapes.large, // behave like Dialogs.
        elevation: CardElevation?,
        content: @Composable (LoadErrorCardScope.() -> Unit),
    )

    @Stable
    data object Neural : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
            )
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier, shape = shape,
                colors = colors,
                elevation = elevation ?: CardDefaults.elevatedCardElevation(),
            ) {
                scope.content()
            }
        }
    }

    @Stable
    data object Unimportant : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier,
                colors = colors, // no 'boxing'
                elevation = elevation ?: CardDefaults.cardElevation(), // no elevation
            ) {
                scope.content()
            }
        }
    }

    @Stable
    data object Important : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.error,
            )
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier, shape = shape,
                colors = colors,
                elevation = elevation ?: CardDefaults.elevatedCardElevation(),
            ) {
                scope.content()
            }
        }
    }

    @Stable
    data object Suggestive : LoadErrorCardRole() {
        @Composable
        override fun Container(
            modifier: Modifier,
            containerColor: Color,
            shape: Shape,
            elevation: CardElevation?,
            content: @Composable (LoadErrorCardScope.() -> Unit),
        ) {
            val colors = CardDefaults.elevatedCardColors(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            val colorsState by rememberUpdatedState(colors)
            val scope = remember {
                object : LoadErrorCardScope {
                    override val cardColors: CardColors @Composable get() = colorsState
                }
            }
            ElevatedCard(
                modifier, shape = shape,
                colors = colors,
                elevation = elevation ?: CardDefaults.elevatedCardElevation(),
            ) {
                scope.content()
            }
        }
    }

    companion object {
        fun from(problem: LoadError): LoadErrorCardRole {
            return when (problem) {
                // Important error
                is LoadError.UnknownError,
                is LoadError.RequestError,
                LoadError.ServiceUnavailable,
                LoadError.NetworkError,
                    -> Important

                // Suggestive message
                LoadError.RequiresLogin -> Suggestive

                // Neutral message
                LoadError.RateLimited -> Neural

                // Unimportant message
                LoadError.NoResults -> Unimportant
            }
        }
    }
}