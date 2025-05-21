package me.him188.ani.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import org.jetbrains.compose.ui.tooling.preview.Preview


@Composable
internal fun ThirdPartyLoginMethods(
    onBangumiClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TextDivider(
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Text("其他登录方式")
        }

        FilledTonalButton(
            onBangumiClick,
            Modifier.fillMaxWidth(),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Image(Icons.Default.BangumiNext, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Bangumi")
        }
    }
}

@Composable
private fun TextDivider(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(color = containerColor) {
        Box(modifier, contentAlignment = Alignment.Center) {
            HorizontalDivider()
            Row(
                Modifier.background(color = containerColor).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyleContentColor(textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) {
                    content()
                }
            }
        }
    }
}

@Composable
@Preview
private fun PreviewThirdPartyLoginMethods() = ProvideCompositionLocalsForPreview {
    Surface {
        ThirdPartyLoginMethods({})
    }
}
