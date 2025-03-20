/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import androidx.annotation.CheckResult
import me.him188.ani.app.domain.media.selector.MaybeExcludedMediaAssertions.Assert
import me.him188.ani.app.domain.media.selector.MaybeExcludedMediaAssertions.AssertNoMoreElements
import me.him188.ani.app.domain.media.selector.MaybeExcludedMediaAssertions.Filter.FilterType
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

context(_: MediaSelectorTestSuite)
inline fun List<MaybeExcludedMedia>.assert(
    block: MaybeExcludedMediaAssertions.() -> Unit,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    MaybeExcludedMediaAssertions().apply(block).runOn(this)
}

context(_: MediaSelectorTestSuite)
@JvmName("assertListMedia")
inline fun List<Media>.assert(
    block: MaybeExcludedMediaAssertions.() -> Unit,
) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    MaybeExcludedMediaAssertions().apply(block).runOn(this.map {
        MaybeExcludedMedia.Included(
            it,
            metadata = MatchMetadata(
                MatchMetadata.SubjectMatchKind.EXACT,
                episodeMatchKind = MatchMetadata.EpisodeMatchKind.SORT,
                similarity = 100,
            )
        )
    })
}

class MaybeExcludedMediaAssertions {
    private val rules = mutableListOf<Rule>()
    private val unusedTargets = mutableListOf<Target>()

    ///////////////////////////////////////////////////////////////////////////
    // Positional Access
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 匹配列表中的下一个元素.
     */
    @CheckResult
    fun next() = Next(getCodeSource("filter")).also {
        unusedTargets.add(it)
    }

    /**
     * 匹配列表中的唯一一个元素.
     */
    @CheckResult
    fun single(): Filter {
        return Filter(
            getCodeSource("filter"),
            FilterType.SINGLE,
            failOnNoMatch = true,
            failIfMoreElement = true,
        ).also {
            unusedTargets.add(it)
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Random Access
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 匹配满足所有条件的一个特定元素. 这将会移动当前指针到该元素, 也就是说 [next] 将会匹配紧接着的下一个元素.
     * 如果条件为 `null`, 则忽略该条件.
     * 需要至少有一个条件, 否则请使用 [onOneOrMore].
     */
    @CheckResult
    fun onSingle(
        sourceId: String? = null,
        mediaId: String? = null,
    ): Filter {
        require(sourceId != null || mediaId != null)
        return Filter(
            getCodeSource("filter"),
            FilterType.SINGLE,
            failOnNoMatch = true,
            failIfMoreElement = false,
            sourceId,
            mediaId
        ).also {
            unusedTargets.add(it)
        }
    }

    /**
     * 匹配至少一个元素.
     */
    @CheckResult
    fun onOneOrMore(
        sourceId: String? = null,
        mediaId: String? = null,
    ): Filter {
        require(sourceId != null || mediaId != null)
        return Filter(
            getCodeSource("filter"),
            FilterType.ALL,
            failOnNoMatch = true,
            failIfMoreElement = false,
            sourceId,
            mediaId
        ).also {
            unusedTargets.add(it)
        }
    }

    /**
     * 匹配 0 个或者更多元素
     */
    @CheckResult
    fun onZeroOrMore(
        sourceId: String? = null,
        mediaId: String? = null,
    ): Filter {
        require(sourceId != null || mediaId != null)
        return Filter(
            getCodeSource("filter"),
            FilterType.ALL,
            failOnNoMatch = false,
            failIfMoreElement = false,
            sourceId,
            mediaId
        ).also {
            unusedTargets.add(it)
        }
    }

    /**
     * 匹配所有元素. 这将会移动当前指针到最后一个元素, 也就是说为 [next] 创建 assertion 将会报错.
     */
    @CheckResult
    fun onAll(): All = All(getCodeSource("filter")).also {
        unusedTargets.add(it)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Assertions
    ///////////////////////////////////////////////////////////////////////////

    fun assertNoMoreElements() {
        rules.add(AssertNoMoreElements(getCodeSource("assertion")))
    }

    // ignorable return value
    /**
     * Assert all medias matched by the target has the specified properties.
     */
    fun Target.assert(
        mediaId: String? = null,
        included: Boolean? = null,
        sourceId: String? = null,
        source: Handle? = null,
        kind: MediaSourceKind? = null,
    ): Target {
        removeUnusedTarget(this)
        check(sourceId == null || source == null) {
            "sourceId and source cannot be set at the same time."
        }

        // Properties can be null, in which case we only assert this element exists.
        addCheck(
            this,
            CheckProperties(
                getCodeSource("assertion"),
                mediaId = mediaId,
                included = included,
                sourceId = sourceId ?: source?.instance?.mediaSourceId,
                kind = kind,
            ),
        )
        return this
    }

    ///////////////////////////////////////////////////////////////////////////

    private fun removeUnusedTarget(target: Target) {
        unusedTargets.remove(target)
    }

    fun runOn(list: List<MaybeExcludedMedia>) {
        unusedTargets.forEach { target ->
            target.codeSource.runWithSourceInfo {
                throw IllegalStateException("Detected unused target: $target. You must call .assert or other assertions on this target.")
            }
        }
        MaybeExcludedMediaAssertionsExecutor(list, rules).execute()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Rules
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 用来匹配待 assert 的 media
     */
    sealed class Target {
        abstract val codeSource: CodeSource
        abstract override fun toString(): String
    }

    data class Next(
        override val codeSource: CodeSource,
    ) : Target()

    data class Filter(
        override val codeSource: CodeSource,
        val type: FilterType,
        val failOnNoMatch: Boolean,
        val failIfMoreElement: Boolean,
        val sourceId: String? = null,
        val mediaId: String? = null,
    ) : Target() {
        enum class FilterType {
            ALL,
            SINGLE
        }
    }

    data class All(
        override val codeSource: CodeSource,
    ) : Target()


    sealed class Rule()

//    data class AssertPosition(
//        val filter: Filter,
//    ) : Rule()

    data class Assert(
        val target: Target,
        val checks: MutableList<Check> = mutableListOf(),
    ) : Rule()

    data class AssertNoMoreElements(
        val source: CodeSource,
    ) : Rule()


    sealed class Check(val source: CodeSource)

    class CheckProperties(
        source: CodeSource,
        @Suppress("unused")
        private val _usePositionalArguments: Nothing? = null,
        val mediaId: String? = null,
        val included: Boolean? = null,
        val sourceId: String? = null,
        val kind: MediaSourceKind? = null,
        val exclusionReason: MediaExclusionReason? = null,
    ) : Check(source)

    private fun addCheck(target: Target, check: Check) {
        for (rule in rules.asReversed()) {
            when (rule) {
                is Assert -> {
                    if (rule.target == target) {
                        rule.checks.add(check)
                        return
                    }
                    continue
                }

                is AssertNoMoreElements -> continue
            }
        }

        // No existing rule, create a new rule
        rules.add(Assert(target, mutableListOf(check)))
    }
}

/**
 * 创建 rule/check 的代码. 用于更好地追踪.
 */
class CodeSource(
    val exception: Exception, // for stacktrace, addSuppressed
)

inline fun <R> CodeSource.runWithSourceInfo(
    block: () -> R,
): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return try {
        block()
    } catch (e: Throwable) {
        e.addSuppressed(this.exception)
        throw e
    }
}


@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@kotlin.internal.InlineOnly // This function call will not be in the stacktrace.
inline fun getCodeSource(name: String): CodeSource = CodeSource(Exception("Source code for $name"))


private class MaybeExcludedMediaAssertionsExecutor(
    private val list: List<MaybeExcludedMedia>,
    private val rules: List<MaybeExcludedMediaAssertions.Rule>,
) {
    private var index = -1

    fun execute() {
        for (rule in rules) {
            when (rule) {
                is Assert -> {
                    val medias = rule.target.resolveOrFail()
                    for (media in medias) {
                        for (check in rule.checks) {
                            check.runOnOrFail(media)
                        }
                    }
                }

                is AssertNoMoreElements -> {
                    rule.source.runWithSourceInfo {
                        assertEquals(
                            index, list.lastIndex,
                            message = "Expected no more elements, but found ${list.lastIndex - index} more elements. Current index = $index, list size = ${list.size}",
                        )
                    }
                }
            }
        }
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    private fun MaybeExcludedMediaAssertions.Target.resolveOrFail(): List<MaybeExcludedMedia> {
        this.codeSource.runWithSourceInfo {
            when (this) {
                is MaybeExcludedMediaAssertions.All -> {
                    return (if (index == -1) {
                        list
                    } else {
                        list.drop(index)
                    }).also {
                        index = list.lastIndex
                    }
                }

                is MaybeExcludedMediaAssertions.Next -> {
                    return listOf(atIndexOrFail(++index))
                }

                is MaybeExcludedMediaAssertions.Filter -> {
                    val matched = list.filter { media ->
                        media.original.matches(this)
                    }

                    if (this.failOnNoMatch) {
                        assertNotEquals(
                            0, matched.size,
                            "Found empty match for filter: $this",
                        )
                    }

                    if (this.failIfMoreElement) {
                        assertEquals(
                            matched.size, list.size,
                            "Found excess elements in the less for filter: $this",
                        )
                    }

                    // Handle match type
                    when (this.type) {
                        FilterType.ALL -> {}
                        FilterType.SINGLE -> {
                            if (matched.size > 1) {
                                assertEquals(
                                    1, matched.size,
                                    "Expected one exact match but found ${matched.size}, for filter: $this",
                                )
                            }
                        }
                    }

                    return matched
                }
            }
        }
    }

    private fun Media.matches(filter: MaybeExcludedMediaAssertions.Filter): Boolean {
        filter.mediaId?.let {
            if (this.mediaId != it) {
                return false
            }
        }

        filter.sourceId?.let {
            if (mediaSourceId != it) {
                return false
            }
        }

        return true
    }

    private fun atIndexOrFail(index: Int): MaybeExcludedMedia {
        return list.getOrNull(index)
            ?: throw AssertionError("Expected one more media at index $index, but found none. List size = ${list.size}")
    }

    @OptIn(UnsafeOriginalMediaAccess::class)
    private fun MaybeExcludedMediaAssertions.Check.runOnOrFail(media: MaybeExcludedMedia) {
        this.source.runWithSourceInfo {
            when (this) {
                is MaybeExcludedMediaAssertions.CheckProperties -> {
                    this.included?.let {
                        assertEquals(it, media is MaybeExcludedMedia.Included)
                    }

                    this.mediaId?.let {
                        assertEquals(it, media.original.mediaId)
                    }

                    this.sourceId?.let {
                        assertEquals(it, media.original.mediaSourceId)
                    }

                    this.kind?.let {
                        assertEquals(it, media.original.kind)
                    }

                    this.exclusionReason?.let {
                        assertEquals(it, media.exclusionReason)
                    }
                }
            }
        }
    }
}
