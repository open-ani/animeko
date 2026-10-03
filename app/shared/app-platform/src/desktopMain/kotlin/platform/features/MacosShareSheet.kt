/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * macOS 的分享菜单: `NSSharingServicePicker`, 通过 JNA 调用 Objective-C 运行时.
 *
 * AppKit 只能在主线程使用, 而调用方运行在 AWT 或协程线程上, 所以用 `dispatch_async_f` 把工作投递到主队列, 投递即视为成功.
 * 菜单从 [ShareAnchor] 所在的位置向上弹出, 没有锚点时贴着内容区.
 *
 * `objc_msgSend` 按固定参数个数声明, 不用可变参数: Apple arm64 上可变参数走栈, 与 Objective-C 方法的调用约定不同.
 * 返回结构体的消息在 x86_64 上走 `objc_msgSend_stret`; arm64 没有这个入口, 仍用 `objc_msgSend`.
 */
internal object MacosShareSheet : SystemShareSheet {
    private val logger = logger<MacosShareSheet>()

    /** 已投递、尚未执行的工作. 回调对象必须保持可达, 否则 JNA 会回收它背后的函数指针. */
    private val pending = ConcurrentHashMap.newKeySet<ShowPickerWork>()

    /** 正在显示的 picker, 由 alloc 持有一次; 菜单显示期间要活着, 到下一次分享再释放. */
    private var shownPicker: Pointer? = null

    override suspend fun shareFile(windowHandle: Long, file: File, title: String, anchor: ShareAnchor?): Boolean =
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
        private val anchor: ShareAnchor?,
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
            val objc = ObjC.INSTANCE
            val handle = Pointer(windowHandle)
            // Compose 给的是 NSWindow; 已经是 NSView 时直接用
            val view = if (objc.msgSendBool(handle, "isKindOfClass:", objc.cls("NSWindow"))) {
                objc.msgSend(handle, "contentView") ?: error("NSWindow has no contentView")
            } else {
                handle
            }
            val url = objc.msgSend(objc.cls("NSURL"), "fileURLWithPath:", objc.nsString(path)) ?: error("NSURL for $path")
            val items = objc.msgSend(objc.cls("NSArray"), "arrayWithObject:", url) ?: error("NSArray")
            val allocated = objc.msgSend(objc.cls("NSSharingServicePicker"), "alloc") ?: error("NSSharingServicePicker alloc")
            val picker = objc.msgSend(allocated, "initWithItems:", items) ?: error("NSSharingServicePicker init")

            val frame = objc.msgSendRect(view, "frame")
            val rect = anchor?.toAppKitRect(frame.height.toFloat(), objc.msgSendBool(view, "isFlipped"))
                ?: ShareAnchor(0f, 0f, frame.width.toFloat(), frame.height.toFloat())
            objc.objc_msgSend(
                picker,
                objc.sel("showRelativeToRect:ofView:preferredEdge:"),
                NSRect.ByValue().apply {
                    x = rect.left.toDouble()
                    y = rect.top.toDouble()
                    width = rect.width.toDouble()
                    height = rect.height.toDouble()
                },
                view,
                NS_MAX_Y_EDGE,
            )
            synchronized(MacosShareSheet) {
                shownPicker?.let { objc.msgSend(it, "release") }
                shownPicker = picker
            }
        }
    }
}

/**
 * 把内容区坐标 (原点左上, y 向下) 的锚点换成 AppKit 视图坐标. 视图没有翻转时原点在左下、y 向上, 用视图高度 [viewHeight] 翻转;
 * 翻转过的视图 (`isFlipped`) 与内容区坐标一致.
 */
internal fun ShareAnchor.toAppKitRect(viewHeight: Float, flipped: Boolean): ShareAnchor =
    if (flipped) this else copy(top = viewHeight - top - height)

@Structure.FieldOrder("x", "y", "width", "height")
internal open class NSRect() : Structure() {
    @JvmField
    var x: Double = 0.0

    @JvmField
    var y: Double = 0.0

    @JvmField
    var width: Double = 0.0

    @JvmField
    var height: Double = 0.0

    class ByValue : NSRect(), Structure.ByValue
}

/** `NSRectEdge.maxY`: 菜单贴着矩形的上边 (AppKit 坐标里 y 最大的一边) 弹出. */
private const val NS_MAX_Y_EDGE = 3L

@Suppress("FunctionName")
private interface ObjC : Library {
    fun objc_getClass(name: String): Pointer?
    fun sel_registerName(name: String): Pointer
    fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer?
    fun objc_msgSend(receiver: Pointer, selector: Pointer, arg: Pointer?): Pointer?
    fun objc_msgSend(receiver: Pointer, selector: Pointer, rect: NSRect.ByValue, view: Pointer, edge: Long): Pointer?

    companion object {
        val INSTANCE: ObjC by lazy { Native.load("objc", ObjC::class.java) }
    }
}

private val objcLibrary: NativeLibrary by lazy { NativeLibrary.getInstance("objc") }

/** 普通返回值的消息入口, 用于按别的返回类型 (如 `BOOL`) 读结果. */
private val msgSendFunction: Function by lazy { objcLibrary.getFunction("objc_msgSend") }

/** 返回结构体的消息入口. */
private val msgSendStretFunction: Function by lazy {
    objcLibrary.getFunction(if (Platform.isARM()) "objc_msgSend" else "objc_msgSend_stret")
}

private fun ObjC.cls(name: String): Pointer = objc_getClass(name) ?: error("Objective-C class $name not found")

private fun ObjC.sel(name: String): Pointer = sel_registerName(name)

private fun ObjC.msgSend(receiver: Pointer, selector: String): Pointer? = objc_msgSend(receiver, sel(selector))

private fun ObjC.msgSend(receiver: Pointer, selector: String, arg: Pointer?): Pointer? =
    objc_msgSend(receiver, sel(selector), arg)

/** 返回 `BOOL` 的消息: 只有最低字节有意义, 按字节读返回值. */
private fun ObjC.msgSendBool(receiver: Pointer, selector: String, arg: Pointer? = null): Boolean {
    val args: Array<Any?> = if (arg == null) arrayOf(receiver, sel(selector)) else arrayOf(receiver, sel(selector), arg)
    return (msgSendFunction.invoke(Byte::class.javaPrimitiveType, args) as Byte).toInt() != 0
}

private fun ObjC.msgSendRect(receiver: Pointer, selector: String): NSRect =
    msgSendStretFunction.invoke(NSRect.ByValue::class.java, arrayOf<Any?>(receiver, sel(selector))) as NSRect

/** `[NSString stringWithUTF8String:]`, 自动释放. */
private fun ObjC.nsString(value: String): Pointer {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val memory = Memory((bytes.size + 1).toLong())
    memory.write(0, bytes, 0, bytes.size)
    memory.setByte(bytes.size.toLong(), 0)
    return msgSend(cls("NSString"), "stringWithUTF8String:", memory) ?: error("NSString")
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
