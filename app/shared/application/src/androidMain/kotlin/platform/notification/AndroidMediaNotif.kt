/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.notification

import android.app.PendingIntent
import android.content.Context
import android.media.MediaMetadata
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.Image
import coil3.annotation.ExperimentalCoilApi
import coil3.asDrawable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import me.him188.ani.R
import me.him188.ani.app.platform.notification.AndroidNotifManager.Companion.EXTRA_REQUEST_CODE
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.utils.coroutines.childScope
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.features.PlaybackSpeed
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

internal class AndroidMediaNotif(
    manager: AndroidNotifManager,
    getContext: () -> Context,
    channelId: String,
    tag: String,
    parentCoroutineContext: CoroutineContext,
) : MediaNotif, AndroidNotif(manager, getContext, channelId) {
    companion object {
        private val actionId = AtomicInteger(10000)
        private fun nextActionId() = actionId.incrementAndGet()

        private val actions = mutableMapOf<Int, () -> Unit>()

        fun handleIntent(
            requestCode: Int,
        ): Boolean {
            val action = actions[requestCode] ?: return false
            action()
            return true
        }
    }

    private val registeredActions = mutableListOf<Int>()

    private val scope = parentCoroutineContext.childScope()
    private val progressTasker = MonoTasker(scope)
    private val stateTasker = MonoTasker(scope)
    private val mediaSession: MediaSessionCompat = MediaSessionCompat(getContext(), tag)
    private val metadata = MediaMetadataCompat.Builder()

    init {
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(MediaSessionCompat.Token.fromToken(mediaSession.sessionToken.token)),
        )
        silent = true
        ongoing = true
    }

    override fun addOneTimeAction(icon: MediaIcon, title: String, action: () -> Unit) {
        val id = nextActionId()
        builder.addAction(
            androidx.core.app.NotificationCompat.Action.Builder(
                IconCompat.createWithResource(
                    getContext(),
                    when (icon) {
                        MediaIcon.PLAY -> R.drawable.play_arrow_24px
                        MediaIcon.PAUSE -> R.drawable.pause_24px
                        MediaIcon.STOP -> R.drawable.stop_24px
                    },
                ),
                title,
                registerPendingIntent(
                    {
                        action()
                        registeredActions.remove(id)
                        actions.remove(id)
                    },
                    id,
                ),
            ).apply {
                setShowsUserInterface(true)
            }.build(),
        )
    }

    override fun onClickContent(action: () -> Unit) {
        builder.setContentIntent(registerPendingIntent(action))
    }

    private fun registerPendingIntent(
        action: () -> Unit,
        actionId: Int = nextActionId(),
    ): PendingIntent {
        registeredActions.add(actionId)
        actions[actionId] = action
        return PendingIntentCompat.getActivity(
            getContext(),
            actionId,
            manager.activityIntent().putExtra(EXTRA_REQUEST_CODE, actionId),
            PendingIntent.FLAG_UPDATE_CURRENT,
            true,
        )!!
    }

    override fun updateMediaMetadata(
        title: String?,
        album: String?,
        artist: String?,
        duration: Duration?
    ) {
        mediaSession.setMetadata(
            metadata.apply {
                if (title != null) putString(MediaMetadata.METADATA_KEY_TITLE, album)
                if (duration != null) putLong(MediaMetadata.METADATA_KEY_DURATION, duration.inWholeMilliseconds)
                if (album != null) putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                if (artist != null) putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            }.build(),
        )
    }

    @ExperimentalCoilApi
    override fun updateAlbumArt(albumArt: Image) {
        setLargeIcon(albumArt)
        mediaSession.setMetadata(
            metadata.apply {
                putBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART,
                    albumArt.asDrawable(getContext().resources).toBitmap(),
                )
            }.build(),
        )
    }

    override fun attachPlayerState(playerState: MediampPlayer) {
        ongoing = true
//        stateTasker.launch {
//            playerState.playbackState
//                .sampleWithInitial(1000)
//                .map {
//                    if (it.isPlaying) {
//                        addOneTimeAction(MediaIcon.PAUSE, "暂停") {
//                            playerState.pause()
//                        }
//                    } else {
//                        addOneTimeAction(MediaIcon.PLAY, "播放") {
//                            playerState.resume()
//                        }
//                    }
//                }.collect()
//        }
        progressTasker.launch {
            combine(
                playerState.playbackState,
                playerState.currentPositionMillis.sample(1000),
                playerState.features.getOrFail(PlaybackSpeed).valueFlow,
            ) { state, position, speed ->

                mediaSession.setPlaybackState(
                    PlaybackStateCompat.Builder().apply {
                        setState(state.toAndroidState(), position, speed)
                    }.build(),
                )
            }.collect()
        }
    }

    override fun release() {
        scope.cancel()
        mediaSession.release()
        super.release()
        this.registeredActions.forEach {
            actions.remove(it)
        }
    }
}

private fun PlaybackState.toAndroidState(): Int {
    return when (this) {
        PlaybackState.READY -> PlaybackStateCompat.STATE_NONE
        PlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
        PlaybackState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
        PlaybackState.PAUSED_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
        PlaybackState.FINISHED -> PlaybackStateCompat.STATE_STOPPED
        PlaybackState.ERROR -> PlaybackStateCompat.STATE_ERROR
    }
}
