package me.him188.ani.app.ui.foundation

import androidx.compose.ui.draganddrop.DragAndDropEvent

actual fun processDragAndDropEventImpl(event: DragAndDropEvent): DragAndDropContent = DragAndDropContent.Unsupported
