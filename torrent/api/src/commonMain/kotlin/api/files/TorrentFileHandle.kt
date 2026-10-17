package me.him188.ani.app.torrent.api.files

import me.him188.ani.app.torrent.api.TorrentSession

/**
 * 表示一个打开的 [TorrentFileEntry].
 *
 * 每个 [TorrentFileEntry] 可以有多个 [TorrentFileHandle], 仅当所有 [TorrentFileHandle] 都被关闭或 [pause] 后, 文件的下载才会被停止.
 */
interface TorrentFileHandle {
    val entry: TorrentFileEntry

    /**
     * 恢复下载并设置优先级
     *
     * 注意, 设置低于 [FilePriority.NORMAL] 可能会导致下载速度缓慢
     *
     * @throws IllegalStateException 当已经 [close] 时抛出
     */
    fun resume(priority: FilePriority = FilePriority.NORMAL)

    /**
     * 暂停下载
     * @throws IllegalStateException 当已经 [close] 时抛出
     */
    fun pause()

    /**
     * 请求提前下载文件中 [byteRange] (相对文件开头的偏移, 闭区间) 覆盖的所有 piece, 用于在即将跳转到某个位置 (如自动跳过 OP 后) 前预缓存那里的数据.
     *
     * 优先级低于当前播放位置的下载窗口, 高于其余 piece, 因此不会拖慢当前位置的下载. 再次调用会替换之前的范围, 传 `null` 取消预缓存.
     *
     * 这是尽力而为的提示: 已经 [close] 时调用不会抛出异常, 而是被忽略.
     */
    fun setPrefetchRange(byteRange: LongRange?)

    /**
     * 停止下载并关闭此 [TorrentFileHandle]. 后续将不能再 [resume] 或 [pause] 等.
     *
     * 若此 torrent 文件是其背后 [TorrentSession] 最后一个要关闭的 torrent 文件，则该函数会挂起，
     * 直到 [TorrentSession] 完全关闭.
     *
     * 如果 [close] 已经被调用过了, 本函数不会有任何效果.
     */
    suspend fun close()

    /**
     * [close] 并且删除文件. 如果已经 close 了, 本函数仍然会删除相关文件.
     */
    suspend fun closeAndDelete()
}
