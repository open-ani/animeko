package me.him188.ani.utils.ipparser

internal actual fun IpSeqRange(ipSeqPattern: String): IpSeqRange = object : IpSeqRange {
    override fun contains(address: String): Boolean = address == ipSeqPattern
}
