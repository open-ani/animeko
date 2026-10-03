/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.isAArch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * macOS 的分享菜单: `NSSharingServicePicker`, 通过 JNA 调用 Objective-C 运行时.
 *
 * AppKit 只能在主线程使用, 而调用方运行在 AWT 或协程线程上, 所以用 `dispatch_async_f` 把工作投递到主队列, 投递即视为成功.
 * 菜单从 [DpRect] 锚点所在的位置向上弹出, 没有锚点时贴着内容区.
 */
internal object MacosShareSheet : SystemShareSheet {
    private val logger = logger<MacosShareSheet>()

    /** 已投递、尚未执行的工作. 回调对象必须保持可达, 否则 JNA 会回收它背后的函数指针. */
    private val pending = ConcurrentHashMap.newKeySet<ShowPickerWork>()

    /** 正在显示的 picker, 由 alloc 持有一次; 菜单显示期间要活着, 到下一次分享再释放. 只在主线程读写. */
    private var shownPicker: Pointer? = null

    override suspend fun shareFile(windowHandle: Long, file: File, anchor: DpRect?): Boolean =
        runCatchingCancellable {
            val work = ShowPickerWork(windowHandle, file.absolutePath, anchor)
            pending += work
            Dispatch.INSTANCE.dispatch_async_f(Dispatch.mainQueue, null, work)
            true
        }.onFailure { logger.warn(it) { "Failed to show the macOS sharing service picker for $file" } }
            .getOrDefault(false)

    /** 主线程上的工作: 创建 picker 并显示. */
    private class ShowPickerWork(
        private val windowHandle: Long,
        private val path: String,
        private val anchor: DpRect?,
    ) : DispatchFunction {
        override fun invoke(context: Pointer?) {
            try {
                showPicker()
            } catch (e: Throwable) {
                logger.warn(e) { "Failed to show the macOS sharing service picker for $path" }
            } finally {
                pending -= this
            }
        }

        private fun showPicker() {
            val handle = Pointer(windowHandle)
            val view = if (ObjC.msgSendBool(handle, "isKindOfClass:", ObjC.cls("NSWindow"))) {
                ObjC.msgSend(handle, "contentView")
            } else {
                handle
            }
            val url = ObjC.msgSend(ObjC.cls("NSURL"), "fileURLWithPath:", ObjC.nsString(path))
            val items = ObjC.msgSend(ObjC.cls("NSArray"), "arrayWithObject:", url)
            val picker = ObjC.msgSend(ObjC.msgSend(ObjC.cls("NSSharingServicePicker"), "alloc"), "initWithItems:", items)

            val frame = ObjC.msgSendRect(view, "frame")
            val rect = anchor?.toAppKitRect(frame.height.toFloat(), ObjC.msgSendBool(view, "isFlipped"))
                ?: DpRect(0.dp, 0.dp, frame.width.dp, frame.height.dp)
            ObjC.msgSend(picker, "showRelativeToRect:ofView:preferredEdge:", NSRect(rect), view, NS_MAX_Y_EDGE)
            shownPicker?.let { ObjC.msgSend(it, "release") }
            shownPicker = picker
        }
    }
}

/**
 * 把内容区坐标 (原点左上, y 向下) 的矩形换成 AppKit 视图坐标. 视图没有翻转时原点在左下、y 向上, 用视图高度 [viewHeight] 翻转;
 * 翻转过的视图 (`isFlipped`) 与内容区坐标一致.
 */
internal fun DpRect.toAppKitRect(viewHeight: Float, flipped: Boolean): DpRect =
    if (flipped) this else DpRect(left, (viewHeight - bottom.value).dp, right, (viewHeight - top.value).dp)

/** 按值传递与返回的 `NSRect`. JNA 需要公开的无参构造器来实例化返回值. */
@Structure.FieldOrder("x", "y", "width", "height")
internal class NSRect() : Structure(), Structure.ByValue {
    @JvmField
    var x: Double = 0.0

    @JvmField
    var y: Double = 0.0

    @JvmField
    var width: Double = 0.0

    @JvmField
    var height: Double = 0.0

    constructor(rect: DpRect) : this() {
        x = rect.left.value.toDouble()
        y = rect.top.value.toDouble()
        width = rect.width.value.toDouble()
        height = rect.height.value.toDouble()
    }
}

/** `NSRectEdge.maxY`: 菜单贴着矩形的上边 (AppKit 坐标里 y 最大的一边) 弹出. */
private const val NS_MAX_Y_EDGE = 3L

/**
 * Objective-C 运行时. `objc_msgSend` 按实际参数逐次调用, 不走 C 可变参数: Apple arm64 上可变参数走栈, 与方法的调用约定不同.
 * 返回结构体的消息在 x86_64 上走 `objc_msgSend_stret`; arm64 没有这个入口, 仍用 `objc_msgSend`.
 */
private object ObjC {
    private val library: NativeLibrary by lazy { NativeLibrary.getInstance("objc") }
    private val getClass: Function by lazy { library.getFunction("objc_getClass") }
    private val registerName: Function by lazy { library.getFunction("sel_registerName") }
    private val msgSend: Function by lazy { library.getFunction("objc_msgSend") }
    private val msgSendStret: Function by lazy {
        library.getFunction(if (currentPlatformDesktop().isAArch()) "objc_msgSend" else "objc_msgSend_stret")
    }

    fun cls(name: String): Pointer = getClass.invokePointer(arrayOf(name)) ?: error("Objective-C class $name not found")

    private fun sel(name: String): Pointer = registerName.invokePointer(arrayOf(name))

    /** 返回对象的消息; nil 视为失败. */
    fun msgSend(receiver: Pointer, selector: String, vararg args: Any?): Pointer =
        msgSend.invokePointer(arrayOf(receiver, sel(selector), *args)) ?: error("[$selector] returned nil")

    /** 返回 `BOOL` 的消息: 只有最低字节有意义, 按字节读返回值. */
    fun msgSendBool(receiver: Pointer, selector: String, vararg args: Any?): Boolean =
        (msgSend.invoke(Byte::class.javaPrimitiveType, arrayOf(receiver, sel(selector), *args)) as Byte).toInt() != 0

    fun msgSendRect(receiver: Pointer, selector: String): NSRect =
        msgSendStret.invoke(NSRect::class.java, arrayOf<Any?>(receiver, sel(selector))) as NSRect

    /** `[NSString stringWithUTF8String:]`, 自动释放. */
    fun nsString(value: String): Pointer {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val memory = Memory((bytes.size + 1).toLong())
        memory.write(0, bytes, 0, bytes.size)
        memory.setByte(bytes.size.toLong(), 0)
        return msgSend(cls("NSString"), "stringWithUTF8String:", memory)
    }
}

/** `dispatch_function_t`: `void (*)(void *context)`. */
private fun interface DispatchFunction : Callback {
    fun invoke(context: Pointer?)
}

@Suppress("FunctionName")
private interface Dispatch : Library {
    fun dispatch_async_f(queue: Pointer, context: Pointer?, work: DispatchFunction)

    companion object {
        val INSTANCE: Dispatch by lazy { Native.load("System", Dispatch::class.java) }

        /** `dispatch_get_main_queue()` 展开为 `&_dispatch_main_q`. */
        val mainQueue: Pointer by lazy {
            NativeLibrary.getInstance("System").getGlobalVariableAddress("_dispatch_main_q")
        }
    }
}
