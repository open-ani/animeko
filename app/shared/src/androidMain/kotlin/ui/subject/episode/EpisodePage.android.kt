package me.him188.ani.app.ui.subject.episode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.danmaku.DummyDanmakuEditor
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Composable
@Preview(widthDp = 1080 / 3, heightDp = 2400 / 3, showBackground = true)
@Preview(device = Devices.TABLET, showBackground = true)
internal fun PreviewEpisodePage(fastSkipTimes: Float = 3f,) {
    ProvideCompositionLocalsForPreview {
        val context = LocalContext.current
        EpisodeScene(
            remember {
                EpisodeViewModel(
                    424663,
                    1277147,
                    context = context,
                )
            },
            fastSkipTimes = fastSkipTimes
        )
    }
}

@Composable
@PreviewLightDark
fun PreviewEpisodeSceneContentPhoneScaffoldTabs() {
    ProvideCompositionLocalsForPreview {
        EpisodeSceneContentPhoneScaffold(
            videoOnly = false,
            commentCount = { 100 },
            video = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
            },
            episodeDetails = { },
            commentColumn = { },
            tabRowContent = {
                DummyDanmakuEditor({})
            },
        )
    }
}
