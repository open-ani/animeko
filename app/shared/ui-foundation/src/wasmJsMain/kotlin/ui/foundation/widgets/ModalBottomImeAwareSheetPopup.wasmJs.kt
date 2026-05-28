package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

@Composable
actual fun ModalBottomImeAwareSheetPopup(
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    properties: PopupProperties,
    content: @Composable () -> Unit
) {
    Popup(popupPositionProvider, onDismissRequest, properties, content)
}
