/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")

package me.him188.ani.app.torrent.api.pieces

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.app.torrent.api.pieces.PieceListSubscriptions.Subscription
import kotlin.jvm.JvmField

/**
 * 高性能 [Piece] 集合. 每个 [PieceList] 一定包含连续的 [Piece.pieceIndex]. 可能为空.
 *
 * [PieceList] 类似于一个 `List<Piece>`, 但不提供 index 方式访问.
 * [PieceList] 只允许通过 [Piece.pieceIndex] 访问: [PieceList.getByPieceIndex].
 *
 *
 * [PieceList] 可以被 [slice] 为一个子集. 子集合与原集合共享相同的数据, 即修改子集合的 [PieceList.state] 会反映到原集合, 反之亦然.
 *
 * 子集合与原集合还共享 [Piece.pieceIndex] 空间.
 * 例如, 从 [Piece.pieceIndex] 范围为 `0..99` 的集合中 slice 出中间 10 个, 即 `50..59`,
 * 则这 10 个 [Piece] 可以同时在原集合和子集合中使用.
 *
 * @see Piece
 */
abstract class PieceList(
    // 这些 array 大小必须相同
    /**
     * 每个 piece 的大小 bytes.
     */
    @JvmField
    val sizes: LongArray, // immutable
    /**
     * piece 的数据偏移量, 即在种子文件中的 offset bytes.
     */
    @JvmField
    val dataOffsets: LongArray,// immutable
    /**
     * 第 0 个元素的 piece index. 如果列表为空则为 `0`.
     */
    @JvmField
    val initialPieceIndex: Int
) {
    /**
     * 所有 piece 的大小之和 bytes
     */
    val totalSize: Long by lazy(LazyThreadSafetyMode.PUBLICATION) { sizes.sum() }

    /**
     * exclusive
     */
    @JvmField
    val endPieceIndex = initialPieceIndex + sizes.size

    abstract val Piece.state: PieceState

    inline val Piece.indexInList get() = pieceIndex - initialPieceIndex

    /**
     * 该 piece 的数据长度 bytes
     */
    inline val Piece.size get() = sizes[indexInList]

    // extensions

    /**
     * 在种子所能下载的所有文件数据中的 offset bytes
     */
    inline val Piece.dataStartOffset: Long get() = dataOffsets[indexInList]

    /**
     * inclusive
     */
    inline val Piece.dataLastOffset: Long get() = dataOffsets[indexInList] + size - 1

    /**
     * exclusive
     */
    inline val Piece.dataEndOffset: Long get() = dataOffsets[indexInList] + size

    inline val Piece.dataOffsetRange: LongRange get() = dataStartOffset..dataLastOffset

    /**
     * 根据 piece 在此列表中的顺序, 计算出它的真实 [Piece.pieceIndex], 然后创建一个 [Piece] 实例.
     * 创建的实例可以在此 PieceList 或有关 slice 中使用.
     *
     * 不会检查是否越界.
     */
    @OptIn(RawPieceConstructor::class)
    // inline is needed to help compiler vectorization
    inline fun createPieceByListIndexUnsafe(listIndex: Int): Piece =
        Piece(initialPieceIndex + listIndex)

    /**
     * @throws IndexOutOfBoundsException
     */
    fun getByPieceIndex(pieceIndex: Int): Piece {
        if (!containsAbsolutePieceIndex(pieceIndex)) {
            throw IndexOutOfBoundsException(
                "pieceIndex $pieceIndex out of bounds " +
                        "${initialPieceIndex}..${initialPieceIndex + sizes.size}",
            )
        }
        return createPieceByListIndexUnsafe(pieceIndex - initialPieceIndex)
    }

//    fun getByListIndex(listIndex: Int): Piece {
//        if (!containsListIndex(listIndex)) {
//            throw IndexOutOfBoundsException("listIndex $listIndex out of bounds")
//        }
//        return createPieceByListIndex(listIndex)
//    }

    /**
     * 挂起当前协程, 直到该 piece 下载完成.
     * 支持 cancellation.
     */
    abstract suspend fun Piece.awaitFinished()

    companion object {
        val Empty = create(0) { 0 }

        fun create(
            numPieces: Int,
            initialDataOffset: Long = 0L,
            initialPieceIndex: Int = 0,
            getPieceSize: (index: Int) -> Long,
        ): MutablePieceList {
            val sizes = LongArray(numPieces) { getPieceSize(it) }
            val offsets = LongArray(numPieces)
            val states = Array(numPieces) { PieceState.READY }

            var pieceOffset = initialDataOffset
            for (i in 0 until numPieces) {
                offsets[i] = pieceOffset
                pieceOffset += sizes[i]
            }

            return DefaultPieceList(sizes, offsets, states, initialPieceIndex)
        }

        fun create(
            totalSize: Long,
            pieceSize: Long,
            initialDataOffset: Long = 0L,
            initialPieceIndex: Int = 0,
        ): MutablePieceList {
            if (totalSize % pieceSize == 0L) {
                return create(
                    (totalSize / pieceSize).toInt(),
                    initialDataOffset,
                    initialPieceIndex,
                    getPieceSize = { pieceSize },
                )
            }

            val numPieces = (totalSize / pieceSize).toInt() + 1
            val sizes = LongArray(numPieces) { pieceSize }
            val offsets = LongArray(numPieces)
            val states = Array(numPieces) { PieceState.READY }

            var pieceOffset = initialDataOffset
            for (i in 0 until numPieces - 1) {
                offsets[i] = pieceOffset
                pieceOffset += sizes[i]
            }

            sizes[numPieces - 1] = totalSize % pieceSize
            offsets[numPieces - 1] = totalSize - (totalSize % pieceSize) + initialDataOffset

            return DefaultPieceList(sizes, offsets, states, initialPieceIndex)
        }

    }
}

sealed class MutablePieceList(
    sizes: LongArray,
    dataOffsets: LongArray,
    initialPieceIndex: Int
) : PieceList(
    sizes,
    dataOffsets,
    initialPieceIndex,
) {
    abstract override var Piece.state: PieceState
    abstract fun Piece.compareAndSetState(expect: PieceState, update: PieceState): Boolean
}


class DefaultPieceList(
    sizes: LongArray, // immutable
    dataOffsets: LongArray, // immutable
    private val states: Array<PieceState>, // mutable
    initialPieceIndex: Int, // 第 0 个元素的 piece index
) : MutablePieceList(sizes, dataOffsets, initialPieceIndex), PieceSubscribable {
    init {
        require(sizes.size == dataOffsets.size) { "sizes.size != dataOffsets.size" }
        require(sizes.size == states.size) { "sizes.size != states.size" }
    }

    private val subscriptions = PieceListSubscriptions()

    override var Piece.state: PieceState
        get() = states[indexInList]
        set(value) {
            states[indexInList] = value
            subscriptions.notifyPieceStateChanges(this@DefaultPieceList, this)
        }

    private val lock = SynchronizedObject()

    override fun Piece.compareAndSetState(expect: PieceState, update: PieceState): Boolean {
        synchronized(lock) {
            if (state == expect) {
                state = update
                return true
            }
            return false
        }
    }

    /**
     * subscribe state changes of all pieces.
     */
    override fun subscribePieceState(piece: Piece, onStateChange: PieceList.(Piece, PieceState) -> Unit): Subscription {
        return subscriptions.subscribe(piece.pieceIndex) { _, p -> onStateChange(p, p.state) }
    }

    /**
     * unsubscribe states
     */
    override fun unsubscribePieceState(subscription: Subscription) {
        subscriptions.unsubscribe(subscription)
    }

    override suspend fun Piece.awaitFinished() {
        val piece = this
        suspendCancellableCoroutine { cont ->
            val subscriptions = subscriptions
            val sub = subscriptions.subscribe(piece.pieceIndex) { subscription, piece ->
                if (piece.state == PieceState.FINISHED) {
                    cont.resumeWith(Result.success(Unit))
                    subscriptions.unsubscribe(subscription)
                }
            }
            cont.invokeOnCancellation {
                subscriptions.unsubscribe(sub)
            }
        }
    }
}

class PieceListSlice(
    private val delegate: MutablePieceList,
    startIndex: Int,
    endIndex: Int,
) : MutablePieceList(
    sizes = delegate.sizes.copyOfRange(startIndex, endIndex),
    dataOffsets = delegate.dataOffsets.copyOfRange(startIndex, endIndex),
    initialPieceIndex = delegate.initialPieceIndex + startIndex,
), PieceSubscribable {
    init {
        require(startIndex >= 0) { "startIndex < 0" }
        require(endIndex <= delegate.sizes.size) { "endIndex > list.sizes.size" }
        require(startIndex <= endIndex) { "startIndex >= endIndex" }
        requireNotNull(delegate as? PieceSubscribable)
    }

    override var Piece.state: PieceState
        get() = with(delegate) { state }
        set(value) {
            with(delegate) {
                state = value
            }
        }

    override fun Piece.compareAndSetState(expect: PieceState, update: PieceState): Boolean {
        with(delegate) {
            return compareAndSetState(expect, update)
        }
    }

    override suspend fun Piece.awaitFinished() {
        with(delegate) {
            awaitFinished()
        }
    }

    // Slice and delegating PieceList share the same Piece index space so we can just forward the call. 
    // See [Piece] for details.
    override fun subscribePieceState(piece: Piece, onStateChange: PieceList.(Piece, PieceState) -> Unit): Subscription {
        return (delegate as PieceSubscribable).subscribePieceState(piece, onStateChange)
    }

    override fun unsubscribePieceState(subscription: Subscription) {
        (delegate as PieceSubscribable).unsubscribePieceState(subscription)
    }
}

fun MutablePieceList.slice(startIndex: Int, endIndex: Int): PieceListSlice {
    require(startIndex >= 0) { "startIndex < 0" }
    require(endIndex <= sizes.size) { "endIndex > sizes.size" }
    require(startIndex <= endIndex) { "startIndex >= endIndex" }
    return PieceListSlice(this, startIndex, endIndex)
}

/**
 * 此列表包含的 piece 数量
 */
val PieceList.count get() = sizes.size

/**
 * 此列表是否为空
 */
fun PieceList.isEmpty() = sizes.isEmpty()

/**
 * 提供一个将各个 [Piece] box 后的 [Sequence]. 性能很低, 仅限性能不敏感场景使用.
 */
fun PieceList.asSequence(): Sequence<Piece> = object : Sequence<Piece> {
    override fun iterator(): Iterator<Piece> = object : Iterator<Piece> {
        private var index = 0
        override fun hasNext(): Boolean = index < sizes.size
        override fun next(): Piece = createPieceByListIndexUnsafe(index++)
    }
}

fun PieceList.containsAbsolutePieceIndex(absolutePieceIndex: Int): Boolean {
    return absolutePieceIndex in initialPieceIndex until endPieceIndex
}

///////////////////////////////////////////////////////////////////////////
// region Collection extensions
///////////////////////////////////////////////////////////////////////////

fun PieceList.first(): Piece {
    if (isEmpty()) throw NoSuchElementException()
    return createPieceByListIndexUnsafe(0)
}

fun PieceList.last(): Piece {
    if (isEmpty()) throw NoSuchElementException()
    return createPieceByListIndexUnsafe(sizes.size - 1)
}

@OptIn(RawPieceConstructor::class)
inline fun <T : PieceList> T.forEach(block: T.(Piece) -> Unit) {
    // Kotlin compiler won't inline ranges so we have to manually write here
    for (i in initialPieceIndex until endPieceIndex) {
        block(Piece(i))
    }
}

inline fun <T : PieceList> T.forEachIndexed(block: T.(index: Int, Piece) -> Unit) {
    val sizes = sizes
    for (i in sizes.indices) {
        block(i, createPieceByListIndexUnsafe(i))
    }
}

inline fun <T : PieceList, C : MutableCollection<E>, E> T.mapTo(destination: C, transform: T.(Piece) -> E): C {
    forEach { p ->
        destination.add(transform(p))
    }
    return destination
}

inline fun <T : PieceList, R> T.mapIndexed(transform: T.(index: Int, Piece) -> R): List<R> {
    val list = ArrayList<R>(this.count)
    forEachIndexed { index, p ->
        list.add(transform(index, p))
    }
    return list
}

inline fun <T : PieceList> T.sumOf(block: T.(Piece) -> Long): Long {
    var sum = 0L
    forEach { p ->
        sum += block(p)
    }
    return sum
}

inline fun <T : PieceList> T.maxOf(block: T.(Piece) -> Long): Long {
    val sizes = sizes
    if (sizes.isEmpty()) {
        throw NoSuchElementException()
    }
    var max = Long.MIN_VALUE
    forEach { p ->
        val value = block(p)
        if (value > max) {
            max = value
        }
    }
    return max
}

inline fun <T : PieceList> T.minOf(block: T.(Piece) -> Long): Long {
    val sizes = sizes
    if (sizes.isEmpty()) {
        throw NoSuchElementException()
    }
    var min = Long.MAX_VALUE
    forEach { p ->
        val value = block(p)
        if (value < min) {
            min = value
        }
    }
    return min
}

inline fun <T : PieceList> T.maxBy(block: T.(Piece) -> Long): Piece {
    val sizes = sizes
    if (sizes.isEmpty()) {
        throw NoSuchElementException()
    }
    var max = Long.MIN_VALUE
    var maxPiece: Piece = Piece.Invalid
    forEach { p ->
        val value = block(p)
        if (value > max) {
            max = value
            maxPiece = p
        }
    }
    check(maxPiece != Piece.Invalid)
    return maxPiece
}

inline fun <T : PieceList> T.minBy(block: T.(Piece) -> Long): Piece {
    val sizes = sizes
    if (sizes.isEmpty()) {
        throw NoSuchElementException()
    }
    var min = Long.MAX_VALUE
    var minPiece: Piece = Piece.Invalid
    forEach { p ->
        val value = block(p)
        if (value < min) {
            min = value
            minPiece = p
        }
    }
    check(minPiece != Piece.Invalid)
    return minPiece
}

/**
 * @see kotlin.collections.indexOfFirst
 */
inline fun <T : PieceList> T.pieceIndexOfFirst(predicate: T.(Piece) -> Boolean): Int {
    forEach { p ->
        if (predicate(p)) {
            return p.pieceIndex
        }
    }
    return -1
}

/**
 * @see kotlin.collections.indexOfLast
 */
@OptIn(RawPieceConstructor::class)
inline fun <T : PieceList> T.pieceIndexOfLast(predicate: T.(Piece) -> Boolean): Int {
    // help compiler
    for (i in (initialPieceIndex until endPieceIndex).reversed()) {
        if (predicate(Piece(i))) {
            return i
        }
    }
    return -1
}

/**
 * @see kotlin.collections.dropWhile
 */
inline fun <T : PieceList> T.dropWhile(predicate: T.(Piece) -> Boolean): List<Piece> {
    val list = mutableListOf<Piece>()
    var found = false
    forEach { p ->
        if (!found && predicate(p)) {
            return@forEach
        }
        found = true
        list.add(p)
    }
    return list
}

/**
 * @see kotlin.collections.takeWhile
 */
inline fun <T : PieceList> T.takeWhile(predicate: T.(Piece) -> Boolean): List<Piece> {
    val list = mutableListOf<Piece>()
    forEach { p ->
        if (predicate(p)) {
            list.add(p)
        } else {
            return list
        }
    }
    return list
}

/**
 * @see kotlin.collections.binarySearch
 */
@OptIn(RawPieceConstructor::class)
inline fun PieceList.binarySearch(
    fromIndex: Int = 0,
    toIndex: Int = count,
    comparator: PieceList.(Piece) -> Int,
): Int {
    var low = createPieceByListIndexUnsafe(fromIndex).pieceIndex
    var high = createPieceByListIndexUnsafe(toIndex - 1).pieceIndex
    while (low <= high) {
        val mid = (low + high).ushr(1)
        val cmp = comparator(Piece(mid))
        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid // key found
        }
    }
    return -1
}

// endregion Collection extensions