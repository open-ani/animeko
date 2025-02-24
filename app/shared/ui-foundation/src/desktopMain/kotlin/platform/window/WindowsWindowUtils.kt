/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.annotation.Keep
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HMENU
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.platform.win32.WinUser.SWP_FRAMECHANGED
import com.sun.jna.platform.win32.WinUser.SWP_NOZORDER
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.SavedWindowsWindowState
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.HTCAPTION
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.HTCLIENT
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.HTCLOSE
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.HTMAXBUTTON
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.HTMINBUTTON
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.MONITOR_DEFAULTTONEAREST
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.SC_RESTORE
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.SWP_NOACTIVATE
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.WS_EX_CLIENTEDGE
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.WS_EX_DLGMODALFRAME
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.WS_EX_STATICEDGE
import me.him188.ani.app.platform.window.ExtendedUser32.Companion.WS_EX_WINDOWEDGE
import kotlin.math.roundToInt

@OptIn(UnsafePlatformWindowApi::class)
class WindowsWindowUtils : AwtWindowUtils() {
    private val dwmAPi: Dwmapi = Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
    private val extendedUser32: ExtendedUser32 =
        Native.load("user32", ExtendedUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)


    override fun setTitleBarColor(hwnd: Long, color: Color): Boolean {
        return setTitleBarColor(HWND(Pointer.createConstant(hwnd)), argbToRgb(color.toArgb()))
    }

    override fun setDarkTitleBar(hwnd: Long, dark: Boolean): Boolean {
        return setDarkTitleBar(HWND(Pointer.createConstant(hwnd)), dark)
    }

    private fun argbToRgb(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        // Windows COLORREF = 0x00BBGGRR
        return (b shl 16) or (g shl 8) or r
    }

    private fun setTitleBarColor(hwnd: HWND, color: Int): Boolean {
        return kotlin.runCatching {
            val colorRef = IntByReference(color)
            W32Errors.SUCCEEDED(
                dwmAPi.DwmSetWindowAttribute(hwnd, Dwmapi.DWMWA_CAPTION_COLOR, colorRef.pointer, DWORD.SIZE),
            )
        }.getOrElse { false }
    }

    private fun setDarkTitleBar(hwnd: HWND, dark: Boolean): Boolean {
        return kotlin.runCatching {
            val isDarkRef = IntByReference(if (dark) 1 else 0)
            W32Errors.SUCCEEDED(
                dwmAPi.DwmSetWindowAttribute(hwnd, Dwmapi.DWMWA_USE_IMMERSIVE_DARK_MODE, isDarkRef.pointer, DWORD.SIZE),
            )
        }.getOrElse { false }
    }

    fun handleBasicWindowProc(
        platformWindow: PlatformWindow,
        windowScope: WindowScope? = platformWindow.windowScope
    ) {
        if (windowScope != null) {
            platformWindow.windowsWindowProc?.close()
            platformWindow.windowsWindowProc = BasicWindowProc(
                extendedUser32,
                windowScope.window,
            )
        }
    }

    fun handleCustomTitleBarWindowProc(
        platformWindow: PlatformWindow,
        windowScope: WindowScope? = platformWindow.windowScope,
        hitTest: (Float, Float) -> Int
    ) {
        if (windowScope != null) {
            platformWindow.windowsWindowProc?.close()
            platformWindow.windowsWindowProc = CustomTitleBarWindowProc(
                windowScope.window,
                extendedUser32,
                dwmAPi,
                hitTest,
            )
        }
    }

    fun windowIsActive(platformWindow: PlatformWindow): Flow<Boolean?> {
        return snapshotFlow { platformWindow.windowsWindowProc }
            .flatMapLatest { (it as? CustomTitleBarWindowProc)?.windowIsActive ?: flowOf(null) }
    }

    fun frameIsColorful(platformWindow: PlatformWindow): Flow<Boolean> {
        return snapshotFlow { platformWindow.windowsWindowProc }
            .flatMapLatest { (it as? CustomTitleBarWindowProc)?.frameIsColorful ?: flowOf(false) }
    }

    fun restoreWindow(windowHandle: Long) {
        extendedUser32.ShowWindow(HWND(Pointer(windowHandle)), WinUser.SW_RESTORE)
    }

    fun minimizeWindow(windowHandle: Long) {
        extendedUser32.CloseWindow(HWND(Pointer(windowHandle)))
    }

    fun maximizeWindow(windowHandle: Long) {
        extendedUser32.ShowWindow(HWND(Pointer(windowHandle)), WinUser.SW_MAXIMIZE)
    }

    fun disposeWindowProc(platformWindow: PlatformWindow) {
        platformWindow.windowsWindowProc?.close()
        platformWindow.windowsWindowProc = null
    }

    fun windowsBuildNumber(): Int? = kotlin.runCatching {
        Advapi32Util.registryGetStringValue(
            WinReg.HKEY_LOCAL_MACHINE,
            "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
            "CurrentBuildNumber",
        ).toIntOrNull()
    }.getOrElse { null }

    // https://learn.microsoft.com/en-us/windows/win32/winmsg/extended-window-styles
    override suspend fun setUndecoratedFullscreen(
        window: PlatformWindow,
        windowState: WindowState,
        undecorated: Boolean
    ) {
        // copied from vlcj
        // uk.co.caprica.vlcj.player.embedded.fullscreen.windows.Win32FullScreenHandler

        val hwnd = HWND(Pointer.createConstant(window.windowHandle))
        if (undecorated) {
            val maximised = extendedUser32.IsZoomed(hwnd)
            if (maximised) {
                // 解除最大化，以便获取原始窗口大小
                extendedUser32.SendMessage(
                    hwnd,
                    User32.WM_SYSCOMMAND,
                    WinDef.WPARAM(SC_RESTORE.toLong()),
                    WinDef.LPARAM(0),
                )
            }
            // Remove window borders and title bar
            val currentStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            if (currentStyle and WinUser.WS_CAPTION == 0) {
                // 目前没有标题, 说明已经是全屏了
                return
            }

            // 保存原始窗口状态
            window.savedWindowsWindowState = SavedWindowsWindowState(
                style = currentStyle,
                exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE),
                rect = WinDef.RECT().apply {
                    User32.INSTANCE.GetWindowRect(hwnd, this)
                }.toComposeRect(),
                maximized = maximised,
            )

            User32.INSTANCE.SetWindowLong(
                hwnd, WinUser.GWL_STYLE,
                currentStyle and (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME).inv(),
            )

            // Remove extended window styles
            User32.INSTANCE.SetWindowLong(
                hwnd, WinUser.GWL_EXSTYLE,
                User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
                    .and((WS_EX_DLGMODALFRAME or WS_EX_WINDOWEDGE or WS_EX_CLIENTEDGE or WS_EX_STATICEDGE).inv()),
            )

            val rect = getMonitorInfo(hwnd).rcMonitor!!

            extendedUser32.SetWindowPos(
                hwnd,
                null,
                rect.left,
                rect.top,
                rect.right - rect.left,
                rect.bottom - rect.top,
                SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
            window.onWindowsUndecoratedFullscreenStateChange(true)
        } else {
            // Restore window borders and title bar
            val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            if (style and WinUser.WS_CAPTION != 0) {
                // 目前有标题, 说明不是全屏
                return
            }

            val savedWindowState = checkNotNull(window.savedWindowsWindowState) {
                "window.savedWindowState is null, cannot restore window state"
            }
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, savedWindowState.style)
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, savedWindowState.exStyle)
            savedWindowState.rect.run {
                User32.INSTANCE.SetWindowPos(
                    hwnd, null,
                    left.roundToInt(), top.roundToInt(), (right - left).roundToInt(), (bottom - top).roundToInt(),
                    SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
                )
            }
            if (savedWindowState.maximized) {
                User32.INSTANCE.SendMessage(
                    hwnd,
                    User32.WM_SYSCOMMAND,
                    WinDef.WPARAM(WinUser.SC_MAXIMIZE.toLong()),
                    WinDef.LPARAM(0),
                )
            }
            window.savedWindowsWindowState = null
            window.onWindowsUndecoratedFullscreenStateChange(false)
        }
    }

    private fun getMonitorInfo(hwnd: HWND): MONITORINFO {
        return MONITORINFO().apply {
            extendedUser32.GetMonitorInfoA(
                extendedUser32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST),
                this,
            )
        }
    }

    override fun setPreventScreenSaver(prevent: Boolean) {
        if (prevent) {
            Kernel32.INSTANCE.SetThreadExecutionState(Kernel32.ES_CONTINUOUS or Kernel32.ES_SYSTEM_REQUIRED or Kernel32.ES_DISPLAY_REQUIRED)
        } else {
            Kernel32.INSTANCE.SetThreadExecutionState(Kernel32.ES_CONTINUOUS)
        }
    }

    companion object {

        val instance: WindowsWindowUtils by lazy { WindowsWindowUtils() }

        val hitClient: Int get() = HTCLIENT

        val hitMaxButton: Int get() = HTMAXBUTTON

        val hitMinimize: Int get() = HTMINBUTTON

        val hitClose: Int get() = HTCLOSE

        val hitCaption: Int get() = HTCAPTION
    }
}

private fun WinDef.RECT.toComposeRect(): Rect {
    return Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}

@Keep
@Suppress("FunctionName", "SpellCheckingInspection")
internal interface Dwmapi : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int

    /**
     * Extends the window frame into the client area.
     *
     * @param hwnd The handle to the window in which the frame will be extended into the client area.
     * @param margins A MARGINS structure that describes the margins to use when extending the frame into the client area.
     * @return If this function succeeds, it returns S_OK. Otherwise, it returns an HRESULT error code.
     */
    fun DwmExtendFrameIntoClientArea(hwnd: HWND, margins: WindowMargins): LRESULT

    // See https://stackoverflow.com/q/62240901
    @Structure.FieldOrder(
        "leftBorderWidth",
        "rightBorderWidth",
        "topBorderHeight",
        "bottomBorderHeight",
    )
    data class WindowMargins(
        @JvmField var leftBorderWidth: Int,
        @JvmField var rightBorderWidth: Int,
        @JvmField var topBorderHeight: Int,
        @JvmField var bottomBorderHeight: Int
    ) : Structure(), Structure.ByReference

    companion object {
        // Windows 10 attribute constant for enabling Immersive Dark Mode
        // Note that this constant is not in official headers for all versions,
        // and might be considered undocumented for some builds.
        const val DWMWA_USE_IMMERSIVE_DARK_MODE: Int = 20
        const val DWMWA_CAPTION_COLOR: Int = 35
    }
}

internal interface ExtendedUser32 : User32 {
    /**
     * Is the window zoomed (maximised) or not?
     *
     * @param hWnd native window handle
     * @return `true` if the window is zoomed; `false` if it is not
     */
    fun IsZoomed(hWnd: HWND?): Boolean

    /**
     * Get a native monitor handle from a window handle.
     *
     * @param hWnd native window handle
     * @param dwFlags flags
     * @return native monitor handle
     */
    fun MonitorFromWindow(hWnd: HWND?, dwFlags: DWORD?): Pointer?

    /**
     * Get native monitor information.
     *
     * @param hMonitor native monitor handle
     * @param lpMonitorInfo structure to receive monitor information
     * @return `true` on success; `false` otherwise
     */
    fun GetMonitorInfoA(hMonitor: Pointer?, lpMonitorInfo: WinUser.MONITORINFO?): Boolean

    /**
     * Send a message to a native window.
     *
     * @param hWnd native window handle
     * @param Msg message identifier
     * @param wParam message parameter
     * @param lParam message parameter
     * @return result
     */
    override fun SendMessage(hWnd: HWND, Msg: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): WinDef.LRESULT

    /**
     * Converts the screen coordinates of a specified point on the screen to client-area coordinates.
     *
     * @param hWnd A handle to the window whose client area will be used for the conversion.
     * @param lpPoint A POINT structure that specifies the screen coordinates to be converted.
     * @return If the function succeeds, the return value is true, else the return value is false.
     */
    fun ScreenToClient(
        hWnd: HWND,
        lpPoint: POINT,
    ): Boolean

    /**
     * Retrieves the specified system metric or system configuration setting taking into account a provided DPI.
     *
     * @param nIndex The system metric or configuration setting to be retrieved. See GetSystemMetrics for the possible values.
     * @param dpi The DPI to use for scaling the metric.
     * @return If the function succeeds, the return value is nonzero, else the return value is zero
     */
    fun GetSystemMetricsForDpi(
        nIndex: Int,
        dpi: UINT,
    ): Int

    /**
     * Returns the dots per inch (dpi) value for the specified window.
     *
     * @param hWnd The window that you want to get information about.
     * @return The DPI for the window, which depends on the DPI_AWARENESS of the window. See the Remarks section for more information. An invalid hwnd value will result in a return value of 0.
     */
    fun GetDpiForWindow(hWnd: HWND): UINT

    /**
     * Retrieves a handle to the system menu of the specified window.
     *
     * The system menu is the menu that appears when the user clicks on the icon in the title bar of the window
     * or presses ALT+SPACE. This function allows the application to access and modify the system menu.
     *
     * @param hWnd A handle to the window that will own the system menu.
     * @param bRevert The action to be taken. If this parameter is FALSE, the function returns a handle
     * to the copy of the system menu currently in use. The copy is initially identical to the default system menu,
     * but it can be modified. If this parameter is TRUE, the function resets the system menu back to the default state.
     * @return If the bRevert parameter is FALSE, the return value is a handle to the copy of the system menu.
     * If the bRevert parameter is TRUE, the return value is NULL.
     */
    fun GetSystemMenu(hWnd: HWND, bRevert: Boolean): HMENU?

    /**
     * Changes information about a menu item.
     *
     * This function allows you to modify various attributes of a menu item, such as its text, state, and ID.
     *
     * @param hMenu A handle to the menu that contains the item.
     * @param uItem The identifier or position of the menu item to change. The meaning of this parameter
     * depends on the value of `fByPosition`.
     * @param fByPosition If this parameter is TRUE, `uItem` is a zero-based relative position. If it is FALSE,
     * `uItem` is a menu item identifier.
     * @param lpmii A pointer to a `MENUITEMINFO` structure that contains information about the menu item
     * and specifies which attributes to change.
     * @return If the function succeeds, the return value is nonzero.
     * If the function fails, the return value is zero. To get extended error information, call `GetLastError`.
     */
    fun SetMenuItemInfo(hMenu: HMENU, uItem: Int, fByPosition: Boolean, lpmii: MENUITEMINFO): Boolean

    /**
     * Displays a shortcut menu at the specified location and tracks the selection of items on the menu.
     *
     * This function is used to display a context menu (usually triggered by a right-click) and allows
     * the user to select an option from the menu. The menu is displayed as a pop-up window.
     *
     * @param hMenu A handle to the shortcut menu to be displayed.
     * @param uFlags The function options. Use a combination of the following flags:
     * - `TPM_LEFTALIGN`: Aligns the menu horizontally so that the left side is at the x-coordinate.
     * - `TPM_RIGHTALIGN`: Aligns the menu horizontally so that the right side is at the x-coordinate.
     * - `TPM_TOPALIGN`: Aligns the menu vertically so that the top is at the y-coordinate.
     * - `TPM_BOTTOMALIGN`: Aligns the menu vertically so that the bottom is at the y-coordinate.
     * - `TPM_RETURNCMD`: Returns the menu item identifier of the user's selection instead of sending a message.
     * @param x The horizontal position of the menu, in screen coordinates.
     * @param y The vertical position of the menu, in screen coordinates.
     * @param nReserved Reserved; must be zero.
     * @param hWnd A handle to the window that owns the shortcut menu.
     * @param prcRect A pointer to a `RECT` structure that specifies an area of the screen the menu should not overlap.
     * If this parameter is null, the function ignores it.
     * @return If the `TPM_RETURNCMD` flag is specified, the return value is the menu item identifier of the item selected by the user.
     * If the user cancels the menu without making a selection or an error occurs, the return value is zero.
     */
    fun TrackPopupMenu(hMenu: HMENU, uFlags: Int, x: Int, y: Int, nReserved: Int, hWnd: HWND, prcRect: RECT?): Int

    /**
     * Sets the default menu item for the specified menu.
     *
     * The default menu item is typically displayed in bold and is activated when the user double-clicks
     * on the menu or presses the Enter key while the menu is open.
     *
     * @param hMenu A handle to the menu whose default item is to be set.
     * @param uItem The identifier or position of the new default menu item. The meaning of this parameter
     * depends on the value of `fByPosition`.
     * @param fByPos If this parameter is TRUE, `uItem` is a zero-based relative position. If it is FALSE,
     * `uItem` is a menu item identifier.
     * @return If the function succeeds, the return value is nonzero.
     * If the function fails, the return value is zero. To get extended error information, call `GetLastError`.
     */
    fun SetMenuDefaultItem(hMenu: HMENU, uItem: Int, fByPos: Boolean): Boolean

    // Contains information about a menu item.
    @Suppress("SpellCheckingInspection", "unused")
    class MENUITEMINFO : Structure() {
        @JvmField
        var cbSize: Int = 0

        @JvmField
        var fMask: Int = 0

        @JvmField
        var fType: Int = 0

        @JvmField
        var fState: Int = 0

        @JvmField
        var wID: Int = 0

        @JvmField
        var hSubMenu: HMENU? = null

        @JvmField
        var hbmpChecked: HBITMAP? = null

        @JvmField
        var hbmpUnchecked: HBITMAP? = null

        @JvmField
        var dwItemData: ULONG_PTR = ULONG_PTR(0)

        @JvmField
        var dwTypeData: String? = null

        @JvmField
        var cch: Int = 0

        @JvmField
        var hbmpItem: HBITMAP? = null

        override fun getFieldOrder(): List<String> {
            return listOf(
                "cbSize", "fMask", "fType", "fState", "wID", "hSubMenu", "hbmpChecked", "hbmpUnchecked",
                "dwItemData", "dwTypeData", "cch", "hbmpItem",
            )
        }
    }

    companion object {
        const val SC_RESTORE: Int = 0x0000f120
        const val SC_MOVE: Int = 0xF010
        const val SC_SIZE: Int = 0xF000
        const val SC_CLOSE: Int = 0xF060

        const val WINT_MAX: Int = 0xFFFF

        const val MIIM_STATE: Int = 0x00000001 // The `fState` member is valid.

        const val MFT_STRING: Int = 0x00000000 // The item is a text string.b

        const val TPM_RETURNCMD: Int =
            0x0100 // Returns the menu item identifier of the user's selection instead of sending a message.

        const val MFS_ENABLED: Int = 0x00000000 // The item is enabled.

        const val MFS_DISABLED: Int = 0x00000003 // The item is disabled.

        // calculate non client area size message
        const val WM_NCCALCSIZE: Int = 0x0083

        // non client area hit test message
        const val WM_NCHITTEST: Int = 0x0084

        // mouse move message
        const val WM_MOUSEMOVE: Int = 0x0200

        // left mouse button down message
        const val WM_LBUTTONDOWN: Int = 0x0201

        // left mouse button up message
        const val WM_LBUTTONUP: Int = 0x0202

        // non client area mouse move message
        const val WM_NCMOUSEMOVE: Int = 0x00A0

        // non client area left mouse down message
        const val WM_NCLBUTTONDOWN: Int = 0x00A1

        // non client area left mouse up message
        const val WM_NCLBUTTONUP: Int = 0x00A2

        // non client area right mouse up message
        val WM_NCRBUTTONUP: Int = 0x00A5

        // window active event
        const val WM_ACTIVATE: Int = 0x0006

        // setting changed message
        const val WM_SETTINGCHANGE: Int = 0x001A

        // window is deactivated
        const val WA_INACTIVE: Int = 0x00000000

        // pass the hit test to parent window
        internal const val HTTRANSPANRENT: Int = -1

        // no hit test
        internal const val HTNOWHERE: Int = 0

        // client area
        const val HTCLIENT: Int = 1

        // title bar
        internal const val HTCAPTION: Int = 2

        //
        internal const val HTMINBUTTON: Int = 8

        // max button
        internal const val HTMAXBUTTON: Int = 9

        // close button
        internal const val HTCLOSE: Int = 20

        // window edges
        internal const val HTLEFT: Int = 10
        internal const val HTRIGHT: Int = 11
        internal const val HTTOP: Int = 12
        internal const val HTTOPLEFT: Int = 13
        internal const val HTTOPRIGHT: Int = 14
        internal const val HTBOTTOM: Int = 15
        internal const val HTBOTTOMLEFT: Int = 16
        internal const val HTBOTTOMRIGHT: Int = 17

        const val WS_THICKFRAME: Int = 0x00040000
        const val WS_CAPTION: Int = 0x00c00000

        const val WS_EX_DLGMODALFRAME: Int = 0x00000001
        const val WS_EX_WINDOWEDGE: Int = 0x00000100
        const val WS_EX_CLIENTEDGE: Int = 0x00000200
        const val WS_EX_STATICEDGE: Int = 0x00020000

        const val SWP_NOZORDER: Int = 0x0004
        const val SWP_NOACTIVATE: Int = 0x0010
        const val SWP_FRAMECHANGED: Int = 0x0020

        val MONITOR_DEFAULTTONEAREST: DWORD = DWORD(2)
    }
}
