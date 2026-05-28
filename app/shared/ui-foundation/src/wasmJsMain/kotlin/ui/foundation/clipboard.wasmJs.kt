@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)

package me.him188.ani.app.ui.foundation

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual fun textClipEntryOf(text: String): ClipEntry = ClipEntry.withPlainText(text)

actual suspend fun Clipboard.getClipEntryText(): String? = getClipEntry()?.fallbackPlainText
