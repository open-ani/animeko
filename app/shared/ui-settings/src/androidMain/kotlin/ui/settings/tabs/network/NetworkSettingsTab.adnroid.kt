/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.domain.media.fetch.MediaFetcher
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.domain.mediasource.instance.MediaSourceSave
import me.him188.ani.app.ui.settings.tabs.media.source.MediaSourceTemplate
import me.him188.ani.app.ui.settings.tabs.media.source.SelectMediaSourceTemplateDialog
import me.him188.ani.datasources.api.matcher.MediaSourceWebVideoMatcherLoader
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceConfig
import me.him188.ani.datasources.api.source.MediaSourceFactory
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.mikan.MikanMediaSource
import me.him188.ani.utils.platform.annotations.TestOnly


@TestOnly
fun createTestMediaSourceInstance(
    instanceId: String, // uuid, to be persisted
    factoryId: FactoryId,
    isEnabled: Boolean,
    config: MediaSourceConfig,
    source: MediaSource,
): MediaSourceInstance = MediaSourceInstance(
    instanceId = instanceId,
    factoryId = factoryId,
    isEnabled = isEnabled,
    config = config,
    source = source,
)

//@OptIn(TestOnly::class)
//@Preview
//@Composable
//private fun PreviewNetworkPreferenceTab() {
//    ProvideCompositionLocalsForPreview(
//        module = {
//            single<MediaSourceManager> {
//                createTestMediaSourceManager()
//            }
//        },
//    ) {
//        val vm = viewModel { NetworkSettingsViewModel() }
//        SideEffect {
//            val testers = vm.mediaSourceTesters.testers
//            if (testers.size < 3) return@SideEffect
//            testers.first().result = ConnectionTestResult.SUCCESS
//            testers.drop(1).first().result = ConnectionTestResult.FAILED
//            testers.drop(2).first().result = ConnectionTestResult.NOT_ENABLED
//        }
//        NetworkSettingsTab()
//    }
//}

@TestOnly
fun createTestMediaSourceManager() = object : MediaSourceManager {
    override val allInstances = MutableStateFlow(
        listOf(
            createTestMediaSourceInstance(
                "1",
                FactoryId("dmhy"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("acg.rip", randomConnectivity = true),
            ),

            createTestMediaSourceInstance(
                "1",
                FactoryId("dmhy"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("dmhy", randomConnectivity = true),
            ),

            createTestMediaSourceInstance(
                "1",
                FactoryId("dmhy"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource(MikanMediaSource.ID, randomConnectivity = true),
            ),

            createTestMediaSourceInstance(
                "1",
                FactoryId("dmhy"),
                true,
                MediaSourceConfig(),
                TestHttpMediaSource("local", randomConnectivity = true),
            ),
        ),
    )
    override val allFactories: List<MediaSourceFactory> = listOf(MikanMediaSource.Factory())
    override val allFactoryIds: List<FactoryId> = allInstances.value.map { it.factoryId }
    override val allFactoryIdsExceptLocal: List<FactoryId>
        get() = allFactoryIds.filter { !isLocal(it) }
    override val mediaFetcher: Flow<MediaFetcher> get() = flowOf()
    override val webVideoMatcherLoader: MediaSourceWebVideoMatcherLoader =
        MediaSourceWebVideoMatcherLoader(flowOf(emptyList()))

    override fun instanceConfigFlow(instanceId: String): Flow<MediaSourceConfig?> {
        return MutableStateFlow(MediaSourceConfig())
    }

    override suspend fun addInstance(
        instanceId: String,
        mediaSourceId: String,
        factoryId: FactoryId,
        config: MediaSourceConfig
    ) {
    }

    override suspend fun getListBySubscriptionId(subscriptionId: String): List<MediaSourceSave> {
        return emptyList()
    }

    override suspend fun partiallyReorderInstances(instanceIds: List<String>) {
    }

    override suspend fun updateConfig(instanceId: String, config: MediaSourceConfig): Boolean {
        return false
    }

    override suspend fun setEnabled(instanceId: String, enabled: Boolean) {
    }

    override suspend fun removeInstance(instanceId: String) {
    }

    override fun mediaSourceTiersFlow(): Flow<MediaSelectorSourceTiers> {
        return flowOf(MediaSelectorSourceTiers.Empty)
    }
}

@Preview
@Composable
private fun PreviewSelectMediaSourceTemplateLayout() {
    SelectMediaSourceTemplateDialog(
        remember {
            listOf(
                MediaSourceTemplate(
                    factoryId = FactoryId("1"),
                    MediaSourceInfo(
                        "Test",
                    ),
                    parameters = MediaSourceParameters.Empty,
                ),
                MediaSourceTemplate(
                    factoryId = FactoryId("123"),
                    MediaSourceInfo(
                        "Test2",
                    ),
                    parameters = MediaSourceParameters.Empty,
                ),
            )
        },
        onClick = {},
        onDismissRequest = {},
    )
}
