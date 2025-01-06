package me.him188.ani.utils.platform.collections

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

/**
 * 每个元素 [K] 一定有值.
 * @see ImmutableEnumMap
 */
interface EnumMap<K : Enum<K>, V> : Map<K, V> {
    override fun get(key: K): V
    override fun containsKey(key: K): Boolean = true
}

@ConsistentCopyVisibility
data class ImmutableEnumMap<K : Enum<K>, V> @PublishedApi internal constructor(
    private val delegate: ImmutableMap<K, V>
) : ImmutableMap<K, V> by delegate, EnumMap<K, V> {
    override fun get(key: K): V {
        return delegate[key] ?: throw NoSuchElementException("Key $key not found in EnumMap")
    }

    override fun containsKey(key: K): Boolean = true
    override fun toString(): String {
        return delegate.toString()
    }
}

fun <K : Enum<K>, V> EnumMap<K, V>.copyPut(
    key: K,
    value: V
): EnumMap<K, V> {
    return ImmutableEnumMap(this.toMutableMap().apply { put(key, value) }.toImmutableMap())
}

fun <K : Enum<K>, V> EnumMap<K, V>.toImmutable(): ImmutableEnumMap<K, V> {
    if (this is ImmutableEnumMap) return this
    return ImmutableEnumMap(this.toImmutableMap())
}

inline fun <reified K : Enum<K>, V> ImmutableEnumMap(
    eachElement: (K) -> V
): ImmutableEnumMap<K, V> {
    val values = enumValues<K>()
    return ImmutableEnumMap(
        buildMap(values.size) {
            for (value in values) {
                put(value, eachElement(value))
            }
        }.toImmutableMap(),
    )
}
