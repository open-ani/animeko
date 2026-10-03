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
import com.sun.jna.CallbackReference
import com.sun.jna.CallbackThreadInitializer
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import com.sun.jna.win32.W32APIOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Windows 的分享面板 (Share UI), 通过 WinRT 的 `DataTransferManager` 实现:
 * 用 `IDataTransferManagerInterop` 取得窗口对应的管理器, 订阅 `DataRequested`, 然后 `ShowShareUIForWindow`;
 * 系统请求数据时把文件作为 StorageItem 和位图填进 `DataPackage`.
 *
 * 所有 COM 调用都在一个专用的 MTA 线程上进行, 事件由系统在线程池线程上回调, 不依赖窗口的消息循环.
 * 需要由 Java 实现的 COM 对象 (事件处理器, 以及装着这一个文件的 `IIterable<IStorageItem>`) 用 JNA 回调拼成虚表 ([JavaComObject]).
 * 文件在显示面板之前就解析成 `StorageFile`, 回调里没有异步操作.
 */
internal object WindowsShareSheet : SystemShareSheet {
    private val logger = logger<WindowsShareSheet>()

    private val comThread: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "WindowsShareSheet").apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }
    private var initialized = false

    /** 上一次分享用到的对象. 面板打开期间系统还会回调它们, 所以保留到下一次分享. */
    private var current: ShareSession? = null

    override suspend fun shareFile(windowHandle: Long, file: File, title: String, anchor: ShareAnchor?): Boolean =
        withContext(comThread) {
            runCatchingCancellable {
                ensureInitialized()
                val session = ShareSession(Pointer(windowHandle), file, title)
                current?.close()
                current = session
                session.show()
                true
            }.onFailure { logger.warn(it) { "Failed to show the Windows share UI for $file" } }
                .getOrDefault(false)
        }

    private fun ensureInitialized() {
        if (initialized) return
        val hr = Combase.INSTANCE.RoInitialize(RO_INIT_MULTITHREADED)
        // S_FALSE: 本线程已初始化; RPC_E_CHANGED_MODE: 已按另一种模式初始化, 仍可使用
        if (hr < 0 && hr != RPC_E_CHANGED_MODE) throw IOException("RoInitialize failed: ${hresult(hr)}")
        initialized = true
    }
}

/** 一次分享: 已解析的文件、注册在管理器上的事件处理器, 以及它们的释放. */
private class ShareSession(private val hwnd: Pointer, file: File, title: String) : AutoCloseable {
    private val owned = mutableListOf<Unknown>()
    private val interop: ComObject = activationFactory(DATA_TRANSFER_MANAGER_CLASS, IID_DATA_TRANSFER_MANAGER_INTEROP).owned()
    private val manager: ComObject =
        ComObject(interop.callOut(INTEROP_GET_FOR_WINDOW, hwnd, IID_DATA_TRANSFER_MANAGER)).owned()
    private val storageFile: ComObject = resolveStorageFile(file)
    private val storageItem: ComObject = storageFile.queryInterface(IID_STORAGE_ITEM)
    private val streamReference: ComObject =
        activationFactory(STREAM_REFERENCE_CLASS, IID_STREAM_REFERENCE_STATICS).use { statics ->
            ComObject(statics.callOut(STREAM_REFERENCE_STATICS_CREATE_FROM_FILE, storageFile.pointer)).owned()
        }
    private val items = StorageItemIterable(storageItem)
    private val handler = DataRequestedHandler(title, items, streamReference)
    private val token: Long = LongByReference().also {
        checkHr(manager.call(DTM_ADD_DATA_REQUESTED, handler.pointer, it), "add_DataRequested")
    }.value

    fun show() {
        checkHr(interop.call(INTEROP_SHOW_SHARE_UI_FOR_WINDOW, hwnd), "ShowShareUIForWindow")
    }

    override fun close() {
        manager.call(DTM_REMOVE_DATA_REQUESTED, token)
        owned.asReversed().forEach { it.Release() }
        owned.clear()
    }

    private fun <T : Unknown> T.owned(): T = also { owned += it }

    private fun ComObject.queryInterface(iid: Guid.IID): ComObject {
        val out = PointerByReference()
        checkHr(QueryInterface(Guid.REFIID(iid), out).toInt(), "QueryInterface(${iid.toGuidString()})")
        return ComObject(out.value).owned()
    }

    /** `StorageFile.GetFileFromPathAsync` 是异步的: 轮询状态直到完成, 有上限. */
    private fun resolveStorageFile(file: File): ComObject =
        activationFactory(STORAGE_FILE_CLASS, IID_STORAGE_FILE_STATICS).use { statics ->
            val operation = HString(file.absolutePath).use { path ->
                ComObject(statics.callOut(STORAGE_FILE_STATICS_GET_FILE_FROM_PATH_ASYNC, path.handle)).owned()
            }
            awaitResult(operation, file)
        }

    private fun awaitResult(operation: ComObject, file: File): ComObject {
        val info = operation.queryInterface(IID_ASYNC_INFO)
        val status = IntByReference()
        val deadline = System.nanoTime() + ASYNC_TIMEOUT_NANOS
        while (true) {
            checkHr(info.call(ASYNC_INFO_GET_STATUS, status), "IAsyncInfo.get_Status")
            when (status.value) {
                ASYNC_STATUS_STARTED -> {
                    if (System.nanoTime() > deadline) throw IOException("GetFileFromPathAsync timed out for $file")
                    Thread.sleep(5)
                }

                ASYNC_STATUS_COMPLETED -> return ComObject(operation.callOut(ASYNC_OPERATION_GET_RESULTS)).owned()

                else -> {
                    val error = IntByReference()
                    info.call(ASYNC_INFO_GET_ERROR_CODE, error)
                    throw IOException("GetFileFromPathAsync failed for $file: status ${status.value}, ${hresult(error.value)}")
                }
            }
        }
    }
}

/** `RoGetActivationFactory`: 运行时类的激活工厂, 按 [iid] 取接口. 调用方负责释放. */
private fun activationFactory(className: String, iid: Guid.IID): ComObject = HString(className).use { name ->
    val out = PointerByReference()
    checkHr(Combase.INSTANCE.RoGetActivationFactory(name.handle, iid, out), "RoGetActivationFactory($className)")
    ComObject(out.value)
}

/** `DataRequested` 的处理器: 把标题、文件和位图填进请求的 `DataPackage`. */
private class DataRequestedHandler(
    private val title: String,
    private val items: StorageItemIterable,
    private val streamReference: ComObject,
) : JavaComObject(
    iids = listOf(IID_UNKNOWN, IID_AGILE_OBJECT, IID_TYPED_EVENT_HANDLER_DATA_REQUESTED),
) {
    override val methods: List<Callback> = listOf(
        InvokeHandlerFn { _, _, args -> invoke(args) },
    )

    private fun invoke(args: Pointer?): Int {
        if (args == null) return E_POINTER
        return try {
            ComObject(ComObject(args).callOut(DATA_REQUESTED_EVENT_ARGS_GET_REQUEST)).use { request ->
                ComObject(request.callOut(DATA_REQUEST_GET_DATA)).use { data ->
                    ComObject(data.callOut(DATA_PACKAGE_GET_PROPERTIES)).use { properties ->
                        HString(title).use { checkHr(properties.call(DATA_PACKAGE_PROPERTY_SET_PUT_TITLE, it.handle), "put_Title") }
                    }
                    checkHr(data.call(DATA_PACKAGE_SET_STORAGE_ITEMS, items.pointer), "SetStorageItems")
                    checkHr(data.call(DATA_PACKAGE_SET_BITMAP, streamReference.pointer), "SetBitmap")
                }
            }
            S_OK
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to fill the share DataPackage" }
            E_FAIL
        }
    }

    private companion object {
        private val logger = logger<DataRequestedHandler>()
    }
}

/** 只装一个 `IStorageItem` 的 `IIterable<IStorageItem>`. */
private class StorageItemIterable(private val item: ComObject) : JavaComObject(
    iids = listOf(IID_UNKNOWN, IID_INSPECTABLE, IID_AGILE_OBJECT, IID_ITERABLE_STORAGE_ITEM),
) {
    /** 发出去的迭代器, 保持可达直到本对象被丢弃. */
    private val iterators = mutableListOf<StorageItemIterator>()

    override val methods: List<Callback> = inspectableMethods() + listOf(
        // First
        OutPointerFn { _, out ->
            val iterator = StorageItemIterator(item)
            synchronized(iterators) { iterators += iterator }
            out.setPointer(0, iterator.pointer)
            S_OK
        },
    )
}

/** 单元素的 `IIterator<IStorageItem>`. */
private class StorageItemIterator(private val item: ComObject) : JavaComObject(
    iids = listOf(IID_UNKNOWN, IID_INSPECTABLE, IID_AGILE_OBJECT, IID_ITERATOR_STORAGE_ITEM),
) {
    private var consumed = false

    override val methods: List<Callback> = inspectableMethods() + listOf(
        // get_Current
        OutPointerFn { _, out ->
            if (consumed) return@OutPointerFn E_BOUNDS
            item.AddRef()
            out.setPointer(0, item.pointer)
            S_OK
        },
        // get_HasCurrent
        OutPointerFn { _, out ->
            out.setByte(0, if (consumed) 0 else 1)
            S_OK
        },
        // MoveNext
        OutPointerFn { _, out ->
            consumed = true
            out.setByte(0, 0)
            S_OK
        },
        // GetMany
        GetManyFn { _, capacity, values, actual ->
            if (!consumed && capacity > 0) {
                item.AddRef()
                values.setPointer(0, item.pointer)
                actual.setInt(0, 1)
                consumed = true
            } else {
                actual.setInt(0, 0)
            }
            S_OK
        },
    )
}

/**
 * 用 JNA 回调拼出虚表的 COM 对象. 前三项是 `IUnknown`; 子类按接口顺序提供其余方法 ([methods]), 继承 `IInspectable` 的接口先放
 * [inspectableMethods]. [iids] 是 `QueryInterface` 应答的接口. 回调与内存由本对象持有, 对象可达期间虚表有效.
 */
private abstract class JavaComObject(private val iids: List<Guid.IID>) {
    protected abstract val methods: List<Callback>

    private val refCount = AtomicInteger(1)
    private val unknownMethods: List<Callback> = listOf(
        QueryInterfaceFn { self, riid, out -> queryInterface(self, riid, out) },
        RefCountFn { refCount.incrementAndGet() },
        RefCountFn { refCount.decrementAndGet() },
    )
    private val vtable: Memory by lazy {
        val all = unknownMethods + methods
        Memory((all.size * Native.POINTER_SIZE).toLong()).apply {
            all.forEachIndexed { index, callback ->
                // 系统从它自己的 RPC 线程回调; 这些线程保持附着在 JVM 上, 不在每次回调后分离
                Native.setCallbackThreadInitializer(callback, callbackThreadInitializer)
                setPointer((index * Native.POINTER_SIZE).toLong(), CallbackReference.getFunctionPointer(callback))
            }
        }
    }
    private val instance: Memory by lazy {
        Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, vtable) }
    }

    /** 可以交给系统的接口指针. */
    val pointer: Pointer get() = instance

    private fun queryInterface(self: Pointer, riid: Pointer?, out: Pointer?): Int {
        if (out == null) return E_POINTER
        val requested = riid?.let { Guid.GUID(it).toGuidString() }
        if (requested != null && iids.any { it.toGuidString().equals(requested, ignoreCase = true) }) {
            refCount.incrementAndGet()
            out.setPointer(0, self)
            return S_OK
        }
        out.setPointer(0, null)
        return E_NOINTERFACE
    }

    protected fun inspectableMethods(): List<Callback> = listOf(
        // GetIids
        GetIidsFn { _, count, iids ->
            count.setInt(0, 0)
            iids.setPointer(0, null)
            S_OK
        },
        // GetRuntimeClassName: 空 HSTRING
        OutPointerFn { _, name ->
            name.setPointer(0, null)
            S_OK
        },
        // GetTrustLevel
        OutPointerFn { _, level ->
            level.setInt(0, TRUST_LEVEL_BASE)
            S_OK
        },
    )
}

/** 按虚表下标调用方法的 COM 接口指针. `IUnknown` 占 0..2, `IInspectable` 再占 3..5. */
private class ComObject(pointer: Pointer) : Unknown(pointer) {
    fun call(index: Int, vararg args: Any?): Int = _invokeNativeInt(index, arrayOf(pointer, *args))

    /** 调用末参数为 `void**` 的方法并返回得到的接口指针. */
    fun callOut(index: Int, vararg args: Any?): Pointer {
        val out = PointerByReference()
        checkHr(call(index, *args, out), "vtable[$index]")
        return out.value ?: throw IOException("vtable[$index] returned a null interface")
    }

    inline fun <R> use(block: (ComObject) -> R): R = try {
        block(this)
    } finally {
        Release()
    }
}

/** 一个 `HSTRING`, 用完释放. */
private class HString(value: String) : AutoCloseable {
    val handle: Pointer? = PointerByReference().also {
        checkHr(Combase.INSTANCE.WindowsCreateString(WString(value), value.length, it), "WindowsCreateString")
    }.value

    override fun close() {
        Combase.INSTANCE.WindowsDeleteString(handle)
    }
}

@Suppress("FunctionName")
private interface Combase : StdCallLibrary {
    fun RoInitialize(initType: Int): Int
    fun RoGetActivationFactory(activatableClassId: Pointer?, iid: Guid.GUID, factory: PointerByReference): Int
    fun WindowsCreateString(sourceString: WString, length: Int, string: PointerByReference): Int
    fun WindowsDeleteString(string: Pointer?): Int

    companion object {
        val INSTANCE: Combase by lazy { Native.load("combase", Combase::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}

private fun interface QueryInterfaceFn : StdCallCallback {
    fun invoke(self: Pointer, riid: Pointer?, out: Pointer?): Int
}

private fun interface RefCountFn : StdCallCallback {
    fun invoke(self: Pointer): Int
}

private fun interface GetIidsFn : StdCallCallback {
    fun invoke(self: Pointer, count: Pointer, iids: Pointer): Int
}

/** 只有一个输出参数的方法: `First`, `get_Current`, `get_HasCurrent`, `MoveNext`, `GetRuntimeClassName`, `GetTrustLevel`. */
private fun interface OutPointerFn : StdCallCallback {
    fun invoke(self: Pointer, out: Pointer): Int
}

private fun interface GetManyFn : StdCallCallback {
    fun invoke(self: Pointer, capacity: Int, values: Pointer, actual: Pointer): Int
}

private fun interface InvokeHandlerFn : StdCallCallback {
    fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
}

private val callbackThreadInitializer = CallbackThreadInitializer(true, false, "WindowsShareSheet-callback")

private fun checkHr(hr: Int, what: String) {
    if (hr < 0) throw IOException("$what failed: ${hresult(hr)}")
}

private fun hresult(hr: Int): String = "HRESULT 0x" + hr.toUInt().toString(16).padStart(8, '0')

private const val S_OK = 0
private const val E_NOINTERFACE = 0x80004002.toInt()
private const val E_POINTER = 0x80004003.toInt()
private const val E_FAIL = 0x80004005.toInt()
private const val E_BOUNDS = 0x8000000B.toInt()
private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
private const val RO_INIT_MULTITHREADED = 1
private const val TRUST_LEVEL_BASE = 0
private const val ASYNC_STATUS_STARTED = 0
private const val ASYNC_STATUS_COMPLETED = 1
private const val ASYNC_TIMEOUT_NANOS = 3_000_000_000L

private const val DATA_TRANSFER_MANAGER_CLASS = "Windows.ApplicationModel.DataTransfer.DataTransferManager"
private const val STORAGE_FILE_CLASS = "Windows.Storage.StorageFile"
private const val STREAM_REFERENCE_CLASS = "Windows.Storage.Streams.RandomAccessStreamReference"

// 接口 IID 与虚表下标取自 Windows SDK 10.0.26100 的头文件 (windows.applicationmodel.datatransfer.h, windows.storage.h,
// windows.storage.streams.h, shobjidl_core.h). 继承 IInspectable 的接口, 自身第一个方法的下标为 6.
private val IID_UNKNOWN = Guid.IID("00000000-0000-0000-C000-000000000046")
private val IID_INSPECTABLE = Guid.IID("AF86E2E0-B12D-4c6a-9C5A-D7AA65101E90")
private val IID_AGILE_OBJECT = Guid.IID("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90")
private val IID_ASYNC_INFO = Guid.IID("00000036-0000-0000-C000-000000000046")
private val IID_DATA_TRANSFER_MANAGER_INTEROP = Guid.IID("3A3DCD6C-3EAB-43DC-BCDE-45671CE800C8")
private val IID_DATA_TRANSFER_MANAGER = Guid.IID("a5caee9b-8708-49d1-8d36-67d25a8da00c")
private val IID_TYPED_EVENT_HANDLER_DATA_REQUESTED = Guid.IID("ec6f9cc8-46d0-5e0e-b4d2-7d7773ae37a0")
private val IID_STORAGE_FILE_STATICS = Guid.IID("5984c710-daf2-43c8-8bb4-a4d3eacfd03f")
private val IID_STORAGE_ITEM = Guid.IID("4207a996-ca2f-42f7-bde8-8b10457a7f30")
private val IID_ITERABLE_STORAGE_ITEM = Guid.IID("bb8b8418-65d1-544b-b083-6d172f568c73")
private val IID_ITERATOR_STORAGE_ITEM = Guid.IID("05b487c2-3830-5d3c-98da-25fa11542dbd")
private val IID_STREAM_REFERENCE_STATICS = Guid.IID("857309dc-3fbf-4e7d-986f-ef3b1a07a964")

private const val INTEROP_GET_FOR_WINDOW = 3
private const val INTEROP_SHOW_SHARE_UI_FOR_WINDOW = 4
private const val DTM_ADD_DATA_REQUESTED = 6
private const val DTM_REMOVE_DATA_REQUESTED = 7
private const val DATA_REQUESTED_EVENT_ARGS_GET_REQUEST = 6
private const val DATA_REQUEST_GET_DATA = 6
private const val DATA_PACKAGE_GET_PROPERTIES = 7
private const val DATA_PACKAGE_SET_BITMAP = 21
private const val DATA_PACKAGE_SET_STORAGE_ITEMS = 23
private const val DATA_PACKAGE_PROPERTY_SET_PUT_TITLE = 7
private const val STORAGE_FILE_STATICS_GET_FILE_FROM_PATH_ASYNC = 6
private const val ASYNC_INFO_GET_STATUS = 7
private const val ASYNC_INFO_GET_ERROR_CODE = 8
private const val ASYNC_OPERATION_GET_RESULTS = 8
private const val STREAM_REFERENCE_STATICS_CREATE_FROM_FILE = 6
