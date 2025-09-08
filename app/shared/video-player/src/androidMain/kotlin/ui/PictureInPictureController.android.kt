package me.him188.ani.app.videoplayer.ui

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.compose.ui.geometry.Rect
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.findActivity
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.togglePause
import java.lang.ref.WeakReference

private const val TAG = "PictureInPictureController"

actual class PictureInPictureController actual constructor(
   private val context: Context,
   private val  player: MediampPlayer
) {
    /**
     * Enters Picture-in-Picture mode and returns to the system home page.
     * The video will continue playing in a small window.
     *
     * @param rect The bounds of the video view in window coordinates
     */
    actual fun enterPictureInPictureMode(rect: Rect) {
        val activity = context.findActivity() ?: return
        val playbackState = player.playbackState.value
        // Ensure video continues playing
        if (!playbackState.isPlaying) {
            player.togglePause()
        }

        // Check if PiP is supported on this device
        if (!isPipSupported(activity)) {
            Log.e(TAG, "Picture-in-Picture is not supported on this device or by this activity")
            Toast.makeText(
                activity,
                "Picture-in-Picture is not supported on this device",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        runCatching {
            // Create PiP params builder
            val pipParams = updatePipActions(activity, player, rect)
            // Enter PiP mode
            activity.enterPictureInPictureMode(pipParams)
        }.onFailure { e ->
            Log.e(TAG,"Failed to enter Picture-in-Picture mode: ${e.message}")
            Toast.makeText(
                activity,
                "Failed to enter Picture-in-Picture mode",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Checks if Picture-in-Picture is supported on the device and by the activity.
     */
    private fun isPipSupported(activity: Activity): Boolean {
        // Check if the app has declared PiP support in the manifest
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }


}

/**
 * Updates PiP actions and parameters.
 */
internal fun updatePipActions(activity: Activity, player: MediampPlayer, rect: Rect? = null): PictureInPictureParams {
    val pipParamsBuilder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(createPipActions(activity, player))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        pipParamsBuilder.setAutoEnterEnabled(true)
    }
    if (rect != null) {
        pipParamsBuilder.setSourceRectHint(
            android.graphics.Rect(
                rect.left.toInt(),
                rect.top.toInt(),
                rect.right.toInt(),
                rect.bottom.toInt()
            )
        )
    }
    return pipParamsBuilder.build()
}

/**
 * Creates PiP actions for the controller.
 */
private fun createPipActions(activity: Activity, player: MediampPlayer): List<RemoteAction> {
    val actions = mutableListOf<RemoteAction>()
    PipActionReceiver.mediaPlayer = player
    PipActionReceiver.currentActivity = WeakReference(activity)
    // 后退按钮 (快退15秒)
    val rewindIntent = Intent(activity, PipActionReceiver::class.java).apply {
        action = PipActionReceiver.ACTION_REWIND
    }
    val rewindPendingIntent = PendingIntent.getBroadcast(
        activity,
        0,
        rewindIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val rewindAction = RemoteAction(
        Icon.createWithResource(activity, android.R.drawable.ic_media_rew),
        "快退",
        "快退15秒",
        rewindPendingIntent
    )
    actions.add(rewindAction)

    // 播放/暂停按钮
    val playPauseIntent = Intent(activity, PipActionReceiver::class.java).apply {
        action = PipActionReceiver.ACTION_PLAY_PAUSE
    }
    val playPausePendingIntent = PendingIntent.getBroadcast(
        activity,
        1,
        playPauseIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val playPauseIcon = if (player.getCurrentPlaybackState().isPlaying) {
        android.R.drawable.ic_media_pause
    } else {
        android.R.drawable.ic_media_play
    }
    val playPauseAction = RemoteAction(
        Icon.createWithResource(activity, playPauseIcon),
        "播放/暂停",
        "播放或暂停视频",
        playPausePendingIntent
    )
    actions.add(playPauseAction)

    // 快进按钮 (快进15秒)
    val fastForwardIntent = Intent(activity, PipActionReceiver::class.java).apply {
        action = PipActionReceiver.ACTION_FAST_FORWARD
    }
    val fastForwardPendingIntent = PendingIntent.getBroadcast(
        activity,
        2,
        fastForwardIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val fastForwardAction = RemoteAction(
        Icon.createWithResource(activity, android.R.drawable.ic_media_ff),
        "快进",
        "快进15秒",
        fastForwardPendingIntent
    )
    actions.add(fastForwardAction)

    return actions
}