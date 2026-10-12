/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.subscription

import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceData
import me.him188.ani.app.domain.mediasource.codec.MediaSourceCodecManager
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater.Companion.calculateDiff
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater.ExistingArgument
import me.him188.ani.app.domain.mediasource.subscription.MediaSourceSubscriptionUpdater.RemoteEntry
import me.him188.ani.datasources.api.source.MediaSourceConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaSourceSubscriptionDiffTest {
    private fun arguments(name: String) = RssMediaSourceArguments.Default.copy(name = name)

    private fun remote(name: String, id: String? = null, supported: Boolean = true): RemoteEntry {
        val data = ExportedMediaSourceData(
            RssMediaSource.FactoryId,
            version = if (supported) 1 else 99,
            arguments = MediaSourceCodecManager.json.encodeToJsonElement(
                RssMediaSourceArguments.serializer(),
                arguments(name),
            ),
            id = id,
        )
        return RemoteEntry(data, if (supported) arguments(name) else null)
    }

    private fun local(name: String, id: String? = null): ExistingArgument {
        val save = MediaSourceSave(
            instanceId = "local-$name",
            mediaSourceId = "local-$name",
            factoryId = RssMediaSource.FactoryId,
            isEnabled = true,
            config = MediaSourceConfig(subscriptionId = "subscription", idInSubscription = id),
        )
        return ExistingArgument(save, arguments(name))
    }

    /**
     * 每个订阅中的数据源对应到的本地 instanceId, 没有对应的为 `null`.
     */
    private fun MediaSourceSubscriptionUpdater.Diff.matchedIds() =
        matched.map { (remote, local) -> remote.name to local?.save?.instanceId }

    private fun MediaSourceSubscriptionUpdater.Diff.removedIds() = removed.map { it.save.instanceId }

    @Test
    fun `matches by name when subscription has no ids`() {
        val diff = calculateDiff(listOf(remote("A"), remote("B")), listOf(local("B"), local("A")))
        assertEquals(listOf("A" to "local-A", "B" to "local-B"), diff.matchedIds())
        assertEquals(emptyList(), diff.removedIds())
    }

    @Test
    fun `adds new and removes missing sources`() {
        val diff = calculateDiff(listOf(remote("A"), remote("B")), listOf(local("A"), local("C")))
        assertEquals(listOf("A" to "local-A", "B" to null), diff.matchedIds())
        assertEquals(listOf("local-C"), diff.removedIds())
    }

    @Test
    fun `renamed source keeps local source when id matches`() {
        val diff = calculateDiff(listOf(remote("A2", id = "a")), listOf(local("A", id = "a")))
        assertEquals(listOf("A2" to "local-A"), diff.matchedIds())
        assertEquals(emptyList(), diff.removedIds())
    }

    @Test
    fun `matches by name when local source has no id yet`() {
        val diff = calculateDiff(listOf(remote("A", id = "a")), listOf(local("A")))
        assertEquals(listOf("A" to "local-A"), diff.matchedIds())
    }

    @Test
    fun `different ids are different sources even with the same name`() {
        val diff = calculateDiff(listOf(remote("A", id = "a2")), listOf(local("A", id = "a1")))
        assertEquals(listOf("A" to null), diff.matchedIds())
        assertEquals(listOf("local-A"), diff.removedIds())
    }

    @Test
    fun `id match takes priority over name match`() {
        val diff = calculateDiff(
            listOf(remote("A", id = "x")),
            listOf(local("A"), local("B", id = "x")),
        )
        assertEquals(listOf("A" to "local-B"), diff.matchedIds())
        assertEquals(listOf("local-A"), diff.removedIds())
    }

    @Test
    fun `unsupported source keeps local source`() {
        val diff = calculateDiff(listOf(remote("A", supported = false)), listOf(local("A")))
        assertEquals(listOf("A" to "local-A"), diff.matchedIds())
        assertEquals(emptyList(), diff.removedIds())
    }

    @Test
    fun `unsupported source without local source is skipped`() {
        val diff = calculateDiff(listOf(remote("A", supported = false), remote("B")), emptyList())
        assertEquals(listOf("B" to null), diff.matchedIds())
    }
}
