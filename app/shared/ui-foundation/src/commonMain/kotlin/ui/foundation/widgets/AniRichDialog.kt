package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * @param buttons aligned to the end; `null` 时整行 (含它的上间距) 不渲染 —— 按钮全部由返回键
 * 代替的形态 (遥控器) 下留着会在底部空出一块
 */
@Composable
fun RichDialogLayout(
    title: @Composable RowScope.() -> Unit,
    buttons: (@Composable RowScope.() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: @Composable (RowScope.() -> Unit)? = null,
    description: @Composable (RowScope.() -> Unit)? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier) {
        Box {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                        title()
                    }
                }

                subtitle?.let {
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            subtitle()
                        }
                    }
                }

                description?.let {
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            description()
                        }
                    }
                }

                Column(Modifier.padding(top = 16.dp)) {
                    content()
                }

                buttons?.let {
                    Row(
                        Modifier.padding(top = 16.dp).align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        it()
                    }
                }
            }

            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                topBarActions()
            }
        }
    }
}


// matches material design
///**
// * @param buttons aligned to the end
// */
//@Composable
//fun RichDialogLayout(
//    title: @Composable RowScope.() -> Unit,
//    buttons: @Composable RowScope.() -> Unit,
//    modifier: Modifier = Modifier,
//    subtitle: @Composable (RowScope.() -> Unit)? = null,
//    description: @Composable (RowScope.() -> Unit)? = null,
//    topBarActions: @Composable RowScope.() -> Unit = {},
//    content: @Composable ColumnScope.() -> Unit,
//) {
//    Surface(
//        modifier.wrapContentSize(),
//        shape = AlertDialogDefaults.shape,
//        tonalElevation = AlertDialogDefaults.TonalElevation,
//        color = AlertDialogDefaults.containerColor,
//    ) {
//        Box {
//            Column(Modifier.padding(16.dp)) {
//                Row(verticalAlignment = Alignment.CenterVertically) {
//                    ProvideTextStyleContentColor(
//                        MaterialTheme.typography.titleLarge,
//                        AlertDialogDefaults.titleContentColor
//                    ) {
//                        title()
//                    }
//                }
//
//                subtitle?.let {
//                    Row(
//                        Modifier.padding(top = 8.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        ProvideTextStyleContentColor(
//                            MaterialTheme.typography.bodyLarge,
//                            AlertDialogDefaults.titleContentColor
//                        ) {
//                            subtitle()
//                        }
//                    }
//                }
//
//                description?.let {
//                    Row(
//                        Modifier.padding(top = 8.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        ProvideTextStyleContentColor(
//                            MaterialTheme.typography.bodyMedium,
//                            AlertDialogDefaults.textContentColor
//                        ) {
//                            description()
//                        }
//                    }
//                }
//
//                Column(Modifier.padding(top = 16.dp)) {
//                    content()
//                }
//
//                Row(
//                    Modifier.padding(top = 16.dp).align(Alignment.End),
//                    horizontalArrangement = Arrangement.spacedBy(8.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    buttons()
//                }
//            }
//
//            Row(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
//                topBarActions()
//            }
//        }
//    }
//}
