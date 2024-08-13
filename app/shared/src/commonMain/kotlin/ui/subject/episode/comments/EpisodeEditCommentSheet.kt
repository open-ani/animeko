package me.him188.ani.app.ui.subject.episode.comments

import ModalBottomImeAwareSheet
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.rememberImeMaxHeight
import me.him188.ani.app.ui.subject.components.comment.EditComment
import me.him188.ani.app.ui.subject.components.comment.EditCommentState
import rememberModalBottomImeAwareSheetState

@Composable
fun EpisodeEditCommentSheet(
    state: EditCommentState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val sheetState = rememberModalBottomImeAwareSheetState()
    
    val contentPadding = 16.dp
    val imePresentMaxHeight by rememberImeMaxHeight()

    SideEffect {
        state.invokeOnSendComplete {
            sheetState.close()
        }
    }

    ModalBottomImeAwareSheet(
        state = sheetState,
        onDismiss = onDismiss,
        modifier = Modifier.ifThen(!state.stickerPanelOpened) { imePadding() },
    ) {
        EditComment(
            state = state,
            modifier = modifier.padding(top = contentPadding).padding(contentPadding),
            stickerPanelHeight = with(density) { imePresentMaxHeight.toDp() },
            controlSoftwareKeyboard = true,
            focusRequester = focusRequester,
        )
    }
}