/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package me.him188.ani.app.videoplayer.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererConfiguration
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import org.openani.mediamp.exoplayer.ExoPlayerAudioTimeStretch
import org.openani.mediamp.exoplayer.internal.WsolaRenderersFactory

/** MediaMP 0.5.3 的 WSOLA 工厂具有 JVM 公共可见性，复用它以保持用户选择的音频处理链。 */
@OptIn(UnstableApi::class)
internal fun subtitleDelayRenderersFactory(context: Context, audio: ExoPlayerAudioTimeStretch, delayMillis: () -> Long): RenderersFactory {
    val factory = if (audio == ExoPlayerAudioTimeStretch.HighQualityWsola) WsolaRenderersFactory(context) else DefaultRenderersFactory(context)
    return RenderersFactory { handler, video, audioListener, text, metadata ->
        factory.createRenderers(handler, video, audioListener, text, metadata).map { renderer ->
            if (renderer.trackType == C.TRACK_TYPE_TEXT) SubtitleDelayRenderer(renderer, delayMillis) else renderer
        }.toTypedArray()
    }
}

/** 只移动字幕渲染时钟，音视频继续使用播放器原时钟。 */
@OptIn(UnstableApi::class)
internal class SubtitleDelayRenderer(renderer: Renderer, private val delayMillis: () -> Long) : ForwardingRenderer(renderer) {
    override fun enable(
        configuration: RendererConfiguration, formats: Array<Format>, stream: SampleStream, positionUs: Long,
        joining: Boolean, mayRenderStartOfStream: Boolean, startPositionUs: Long, offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        super.enable(configuration, formats, stream, positionUs - delayMillis() * 1_000,
            joining, mayRenderStartOfStream, startPositionUs, offsetUs, mediaPeriodId)
    }

    override fun resetPosition(positionUs: Long, joining: Boolean) {
        super.resetPosition(positionUs - delayMillis() * 1_000, joining)
    }

    override fun supportsResetPositionWithoutKeyFrameReset(positionUs: Long): Boolean =
        super.supportsResetPositionWithoutKeyFrameReset(positionUs - delayMillis() * 1_000)

    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        super.render(positionUs - delayMillis() * 1_000, elapsedRealtimeUs)
    }

    override fun getDurationToProgressUs(positionUs: Long, elapsedRealtimeUs: Long): Long =
        super.getDurationToProgressUs(positionUs - delayMillis() * 1_000, elapsedRealtimeUs)
}
