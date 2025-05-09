/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// TODO: 2023/12/30 Make Cache.kt internal

package me.him188.ani.datasources.dmhy.impl.cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.datasources.api.topic.Alliance
import me.him188.ani.datasources.api.topic.Author
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import me.him188.ani.datasources.api.topic.Tag
import me.him188.ani.datasources.dmhy.DmhyCategory

internal fun interface Observer<T> {
    fun onChange(newValue: T)

    fun onAttach() {}
    fun onDetach() {}
}

internal interface ObservableProperty<T> {
    val value: T

    val observers: Iterator<Observer<T>>
    fun addObserver(observer: Observer<T>)
    fun removeObserver(observer: Observer<T>)
}

internal interface MutableObservableProperty<T> : ObservableProperty<T> {
    override var value: T
}


interface Cache {
    val users: KeyedMutableListFlow<String, Author>
    val categories: KeyedMutableListFlow<String, DmhyCategory>
    val alliances: KeyedMutableListFlow<String, Alliance>
    val subtitleLanguages: KeyedMutableListFlow<String, SubtitleLanguage>
    val tags: KeyedMutableListFlow<String, Tag>

    fun mergeFrom(cache: Cache)
}

internal class CacheImpl : Cache {
    override val users: KeyedMutableListFlow<String, Author> = KeyedMutableListFlowImpl { it.id }
    override val categories: KeyedMutableListFlow<String, DmhyCategory> = KeyedMutableListFlowImpl { it.id }
    override val alliances: KeyedMutableListFlow<String, Alliance> = KeyedMutableListFlowImpl { it.id }
    override val subtitleLanguages: KeyedMutableListFlow<String, SubtitleLanguage> = KeyedMutableListFlowImpl { it.id }
    override val tags: KeyedMutableListFlow<String, Tag> = KeyedMutableListFlowImpl { it.id }

    override fun mergeFrom(cache: Cache) {
        users.mutate { list -> (list + cache.users).distinctBy { it.id } }
        categories.mutate { list -> (list + cache.categories).distinctBy { it } }
    }

    override fun toString(): String {
        return buildString {
            appendLine("Cache {")
            appendLine("  users: $users")
            appendLine("  categories: $categories")
            appendLine("}")
        }
    }
}

interface ListFlow<T : Any> : Iterable<T> {
    val value: List<T>
    fun asFlow(): StateFlow<List<T>>
    fun asList(): List<T>
    fun asSequence(): Sequence<T> = value.asSequence()
}

class ListFlowImpl<T : Any>(override val value: List<T>) : ListFlow<T> {
    private val flowImpl = MutableStateFlow(value)
    override fun asFlow(): StateFlow<List<T>> = flowImpl
    override fun asList(): List<T> = value
    override fun iterator(): Iterator<T> = value.iterator()
}

interface MutableListFlow<T : Any> : ListFlow<T>, Iterable<T> {
    override var value: List<T>
    fun asMutableFlow(): MutableStateFlow<List<T>>
}

interface KeyedMutableListFlow<K, T : Any> : MutableListFlow<T> {
    operator fun get(key: K): T?
    operator fun set(key: K, value: T?)
}

inline fun <K, T : Any> KeyedMutableListFlow<K, T>.getOrSet(key: K, default: () -> T): T {
    var value = get(key)
    if (value == null) {
        value = default()
        set(key, value)
    }
    return value
}

inline fun <K, T : R, R> KeyedMutableListFlow<K, T & Any>.getOrDefault(key: K, default: () -> R): R {
    return get(key) ?: return default()
}

fun <T : Any> mutableListFlowOf(initial: List<T>): MutableListFlow<T> {
    return MutableListFlowImpl(MutableStateFlow(initial))
}

open class MutableListFlowImpl<T : Any>(
    private val delegate: MutableStateFlow<List<T>> = MutableStateFlow(listOf()),
) :
    Iterable<T>, MutableListFlow<T> {
    override var value: List<T>
        get() = delegate.value
        set(value) {
            delegate.value = value
        }

    override fun asFlow(): StateFlow<List<T>> = delegate

    override fun asMutableFlow(): MutableStateFlow<List<T>> = delegate

    private val list by lazy { DynamicDelegateList { delegate.value } }
    override fun asList(): List<T> = list

    override operator fun iterator(): Iterator<T> = value.iterator()
}

class KeyedMutableListFlowImpl<K, T : Any>(
    private val getKey: (T) -> K,
) : KeyedMutableListFlow<K, T>, MutableListFlowImpl<T>() {
    private inline val T.key get() = getKey(this)

    override fun get(key: K): T? {
        return this.asSequence().find { it.key == key }
    }

    override fun set(key: K, value: T?) {
        return mutate { list ->
            buildList(list.size) {
                for (element in list) {
                    if (element.key == key) {
                        if (value != null) add(value)
                    } else {
                        add(element)
                    }
                }
            }
        }
    }

}

inline fun <T : Any> MutableListFlow<T>.mutate(transform: (List<T>) -> List<T>) {
    value = value.let(transform)
}

private class DynamicDelegateList<T>(
    private val supplier: () -> List<T>,
) : List<T>, AbstractList<T>() {
    override val size: Int get() = supplier().size
    override fun get(index: Int): T = supplier()[index]
    override fun isEmpty(): Boolean = supplier().isEmpty()
    override fun iterator(): Iterator<T> = supplier().iterator()
    override fun listIterator(): ListIterator<T> = supplier().listIterator()
    override fun listIterator(index: Int): ListIterator<T> = supplier().listIterator()
    override fun subList(fromIndex: Int, toIndex: Int): List<T> = supplier().subList(fromIndex, toIndex)
    override fun lastIndexOf(element: T): Int = supplier().lastIndexOf(element)
    override fun indexOf(element: T): Int = supplier().indexOf(element)
    override fun containsAll(elements: Collection<T>): Boolean = supplier().containsAll(elements)
    override fun contains(element: T): Boolean = supplier().contains(element)
}

inline fun <T> MutableStateFlow<T>.mutate(block: (T) -> T) {
    value = value.let(block)
}
