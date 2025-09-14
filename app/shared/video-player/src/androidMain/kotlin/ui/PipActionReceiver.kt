package me.him188.ani.app.videoplayer.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import me.him188.ani.app.platform.Context
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.togglePause
import java.lang.ref.WeakReference

class PipActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_PLAY_PAUSE = "pip_action_play_pause"
        const val ACTION_REWIND = "pip_action_rewind"
        const val ACTION_FAST_FORWARD = "pip_action_fast_forward"

        // 存储MediampPlayer实例的静态引用
        var mediaPlayer: MediampPlayer? = null

        var currentActivity: WeakReference<Activity>? = null
    }

    override fun onReceive(context: Context, intent: Intent) {

        val player = mediaPlayer ?: return
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                player.togglePause()
            }

            ACTION_REWIND -> {
                player.skip(-15000) // 后退15秒
            }

            ACTION_FAST_FORWARD -> {
                player.skip(15000) // 快进15秒
            }
        }

        // 更新PiP控制按钮状态
        currentActivity?.get()?.let { activity ->
            val params = updatePipActions(activity, player)
            activity.setPictureInPictureParams(params)
        }
    }

}