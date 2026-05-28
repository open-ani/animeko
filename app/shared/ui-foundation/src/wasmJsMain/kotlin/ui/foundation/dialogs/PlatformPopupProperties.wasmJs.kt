package me.him188.ani.app.ui.foundation.dialogs

import androidx.compose.ui.window.PopupProperties

@Suppress("FunctionName")
actual fun PlatformPopupPropertiesImpl(
    focusable: Boolean,
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
    usePlatformDefaultWidth: Boolean,
    excludeFromSystemGesture: Boolean,
    clippingEnabled: Boolean,
    usePlatformInsets: Boolean,
): PopupProperties = PopupProperties(
    focusable = focusable,
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    clippingEnabled = clippingEnabled,
)
