/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.ui

/*
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.binding.internal.libvlc_display_callback_t
import uk.co.caprica.vlcj.binding.internal.libvlc_lock_callback_t
import uk.co.caprica.vlcj.binding.internal.libvlc_unlock_callback_t
import uk.co.caprica.vlcj.binding.internal.libvlc_video_cleanup_cb
import uk.co.caprica.vlcj.binding.internal.libvlc_video_format_cb
import uk.co.caprica.vlcj.binding.lib.LibVlc
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat

*/
/**
 * Implementation of a video surface that uses native callbacks to receive video frame data for rendering.
 *//*

class SkiaVideoSurface(
    private val renderCallback: (Bitmap) -> Unit,
    videoSurfaceAdapter: VideoSurfaceAdapter?
) : VideoSurface(videoSurfaceAdapter) {
//    private val bufferFormatCallback: BufferFormatCallback = DefaultBufferFormatCallback()

//    private inner class DefaultBufferFormatCallback : BufferFormatCallbackAdapter() {
//        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
//
//            return BufferFormat("RV32", sourceWidth, height, intArrayOf(width * 4), intArrayOf(height))
//        }
//    }

    private val setup: libvlc_video_format_cb = SetupCallback()
    private val cleanup: libvlc_video_cleanup_cb = CleanupCallback()
    private val lock: libvlc_lock_callback_t = LockCallback()
    private val unlock: libvlc_unlock_callback_t = UnlockCallback()
    private val display: libvlc_display_callback_t = DisplayCallback()

    private lateinit var bitmap: Bitmap

    private var mediaPlayer: MediaPlayer? = null


    override fun attach(mediaPlayer: MediaPlayer) {
        this.mediaPlayer = mediaPlayer

        LibVlc.libvlc_video_set_format_callbacks(mediaPlayer.mediaPlayerInstance(), setup, cleanup)
        LibVlc.libvlc_video_set_callbacks(mediaPlayer.mediaPlayerInstance(), lock, unlock, display, null)
    }

    */
/**
 * Implementation of a callback invoked by the native library to set up the required video buffer characteristics.
 *
 * This callback is invoked when the video format changes.
 *//*

    private inner class SetupCallback : libvlc_video_format_cb {
        override fun format(
            opaque: PointerByReference,
            chroma: PointerByReference,
            width: IntByReference,
            height: IntByReference,
            pitches: PointerByReference,
            lines: PointerByReference
        ): Int {
            val imageInfo = ImageInfo.makeN32Premul(width.value, height.value)
            bitmap = Bitmap()
            check(
                bitmap.allocPixels(imageInfo),
            ) {
                "Failed bitmap.allocPixels"
            }
            val format =
                BufferFormat("RV32", width.value, height.value, intArrayOf(width.value * 4), intArrayOf(height.value))
            applyBufferFormat(format, chroma, width, height, pitches, lines)
            return format.planeCount
        }

        */
/**
 * Set the desired video format properties - space for these structures is already allocated by LibVlc, we
 * simply fill the existing memory.
 *
 *
 * The [BufferFormat] class restricts the chroma to maximum four bytes, so we don't need check it here, we
 * do however need to check if it is less than four.
 *
 * @param chroma
 * @param width
 * @param height
 * @param pitches
 * @param lines
 *//*

        private fun applyBufferFormat(
            bufferFormat: BufferFormat,
            chroma: PointerByReference,
            width: IntByReference,
            height: IntByReference,
            pitches: PointerByReference,
            lines: PointerByReference
        ) {
            return
            val chromaBytes = bufferFormat.chroma.toByteArray()
            chroma.pointer.write(0, chromaBytes, 0, if (chromaBytes.size < 4) chromaBytes.size else 4)
            width.value = bufferFormat.width
            height.value = bufferFormat.height
            val pitchValues = bufferFormat.pitches
            val lineValues = bufferFormat.lines
            pitches.pointer.write(0, pitchValues, 0, pitchValues.size)
            lines.pointer.write(0, lineValues, 0, lineValues.size)
        }
    }

    private inner class CleanupCallback : libvlc_video_cleanup_cb {
        override fun cleanup(opaque: Pointer) {
            bitmap.close()
        }
    }

    private inner class LockCallback : libvlc_lock_callback_t {
        override fun lock(opaque: Pointer, planes: PointerByReference): Pointer? {
            @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
            val ptr = bitmap._ptr
            planes.pointer.write(0, longArrayOf(ptr), 0, 1)
            return null
        }
    }

    private inner class UnlockCallback : libvlc_unlock_callback_t {
        override fun unlock(opaque: Pointer, picture: Pointer, plane: Pointer) {
        }
    }

    */
/**
 * Implementation of a callback invoked by the native library to render a
 * single frame of video.
 *
 * This callback is invoked every frame.
 *//*

    private inner class DisplayCallback : libvlc_display_callback_t {
        override fun display(opaque: Pointer, picture: Pointer) {
            renderCallback(bitmap)
//            renderCallback.display(mediaPlayer, nativeBuffers.buffers(), bufferFormat)
        }
    }
}
*/
