package me.him188.ani.app.videoplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.component.InputEvents
import uk.co.caprica.vlcj.player.component.MediaPlayerComponent
import uk.co.caprica.vlcj.player.component.callback.CallbackImagePainter
import uk.co.caprica.vlcj.player.component.callback.ScaledCallbackImagePainter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.fullscreen.FullScreenStrategy
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt


open class ComposeMediaPlayerComponent @JvmOverloads constructor(
    mediaPlayerFactory: MediaPlayerFactory? = null,
    fullScreenStrategy: FullScreenStrategy? = null,
    inputEvents: InputEvents? = null,
    lockBuffers: Boolean = true,
    imagePainter: CallbackImagePainter? = null,
    renderCallback: RenderCallback? = null,
    bufferFormatCallback: BufferFormatCallback? = null,
) : MediaPlayerComponent {
    /**
     * Flag true if this component created the media player factory, or false if it was supplied by the caller.
     */
    private val ownFactory: Boolean

    /**
     * Media player factory.
     */
    protected val mediaPlayerFactory: MediaPlayerFactory

    /**
     * Default render callback implementation, will be `null` if the client application provides its own
     * render callback.
     */
    private var defaultRenderCallback: DefaultRenderCallback? = null

    /**
     * Painter used to render the video, will be `null`. if the client application provides its own render
     * callback.
     *
     *
     * Ordinarily set via constructor, but may be changed via [.setImagePainter].
     */
    private var imagePainter: CallbackImagePainter? = null

    /**
     * Component used as the video surface.
     */
    private var videoSurfaceComponent: DefaultVideoSurfaceComponent? = null

    /**
     * Media player.
     */
    private val mediaPlayer: EmbeddedMediaPlayer

    /**
     * Image used to render the video.
     */
    private var image: BufferedImage? = null

    init {
        var renderCallback = renderCallback
        var bufferFormatCallback = bufferFormatCallback
        this.ownFactory = mediaPlayerFactory == null
        this.mediaPlayerFactory = initMediaPlayerFactory(mediaPlayerFactory)

        validateArguments(imagePainter, renderCallback, bufferFormatCallback)

        if (renderCallback == null) {
            this.defaultRenderCallback = DefaultRenderCallback()
            this.imagePainter = imagePainter ?: ScaledCallbackImagePainter()
            this.videoSurfaceComponent = DefaultVideoSurfaceComponent()
            bufferFormatCallback = DefaultBufferFormatCallback()
            renderCallback = this.defaultRenderCallback
        } else {
            this.defaultRenderCallback = null
            this.imagePainter = null
        }

        this.mediaPlayer = this.mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer()
        mediaPlayer.fullScreen().strategy(fullScreenStrategy)
//        mediaPlayer.events().addMediaPlayerEventListener(this)
//        mediaPlayer.events().addMediaEventListener(this)

        mediaPlayer.videoSurface().set(
            this.mediaPlayerFactory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, lockBuffers),
        )

//        setBackground(Color.black)
//        setLayout(BorderLayout())
//        if (this.videoSurfaceComponent != null) {
//            add(this.videoSurfaceComponent, BorderLayout.CENTER)
//        }

        initInputEvents(inputEvents)

//        onAfterConstruct()
    }

    /**
     * Construct a callback media list player component for external rendering (by the client application).
     *
     * @param mediaPlayerFactory media player factory
     * @param fullScreenStrategy full screen strategy
     * @param inputEvents keyboard/mouse input event configuration
     * @param lockBuffers `true` if the native video buffer should be locked; `false` if not
     * @param renderCallback render callback
     * @param bufferFormatCallback buffer format callback
     * @param videoSurfaceComponent lightweight video surface component
     */
    constructor(
        mediaPlayerFactory: MediaPlayerFactory?,
        fullScreenStrategy: FullScreenStrategy?,
        inputEvents: InputEvents?,
        lockBuffers: Boolean,
        renderCallback: RenderCallback?,
        bufferFormatCallback: BufferFormatCallback?,
    ) : this(
        mediaPlayerFactory,
        fullScreenStrategy,
        inputEvents,
        lockBuffers,
        null,
        renderCallback,
        bufferFormatCallback,
    )

    /**
     * Create a callback media player component with LibVLC initialisation arguments and reasonable defaults.
     *
     * @param libvlcArgs LibVLC initialisation arguments
     */
    constructor(vararg libvlcArgs: String?) : this(
        MediaPlayerFactory(*libvlcArgs),
        null,
        null,
        true,
        null,
        null,
        null,
    )

    /**
     * Validate that arguments are passed for either intrinsic or external rendering, but not both.
     *
     * @param imagePainter image painter (video renderer)
     * @param renderCallback render callback
     * @param bufferFormatCallback buffer format callback
     * @param videoSurfaceComponent video surface component
     */
    private fun validateArguments(
        imagePainter: CallbackImagePainter?,
        renderCallback: RenderCallback?,
        bufferFormatCallback: BufferFormatCallback?,
    ) {
        if (renderCallback == null) {
            require(bufferFormatCallback == null) { "Do not specify bufferFormatCallback without a renderCallback" }
        } else {
            require(imagePainter == null) { "Do not specify imagePainter with a renderCallback" }
            requireNotNull(bufferFormatCallback) { "bufferFormatCallback is required with a renderCallback" }
        }
    }

    private fun initMediaPlayerFactory(mediaPlayerFactory: MediaPlayerFactory?): MediaPlayerFactory {
        var mediaPlayerFactory = mediaPlayerFactory
        if (mediaPlayerFactory == null) {
            mediaPlayerFactory = MediaPlayerFactory(*DEFAULT_FACTORY_ARGUMENTS)
        }
        return mediaPlayerFactory
    }

    private fun initInputEvents(inputEvents: InputEvents?) {
        var inputEvents = inputEvents
        if (inputEvents == null) {
            inputEvents =
                if (RuntimeUtil.isNix() || RuntimeUtil.isMac()) InputEvents.DEFAULT else InputEvents.DISABLE_NATIVE
        }
        when (inputEvents) {
            InputEvents.NONE -> {}
            InputEvents.DISABLE_NATIVE -> {
                mediaPlayer.input().enableKeyInputHandling(false)
                mediaPlayer.input().enableMouseInputHandling(false)
//                if (videoSurfaceComponent != null) {
//                    videoSurfaceComponent.addMouseListener(this)
//                    videoSurfaceComponent.addMouseMotionListener(this)
//                    videoSurfaceComponent.addMouseWheelListener(this)
//                    videoSurfaceComponent.addKeyListener(this)
//                }
            }

            InputEvents.DEFAULT -> {}
//                if (videoSurfaceComponent != null) {
//                    videoSurfaceComponent.addMouseListener(this)
//                    videoSurfaceComponent.addMouseMotionListener(this)
//                    videoSurfaceComponent.addMouseWheelListener(this)
//                    videoSurfaceComponent.addKeyListener(this)
//                }
        }
    }

    /**
     * Set a new image painter.
     *
     *
     * The image painter should only be changed when the media is stopped, changing an image painter during playback has
     * undefined behaviour.
     *
     *
     * This is *not* used if the application has supplied its own [RenderCallback] on instance creation.
     *
     * @param imagePainter image painter
     */
    fun setImagePainter(imagePainter: CallbackImagePainter?) {
        this.imagePainter = imagePainter
    }

    /**
     * Get the embedded media player reference.
     *
     *
     * An application uses this handle to control the media player, add listeners and so on.
     *
     * @return media player
     */
    fun mediaPlayer(): EmbeddedMediaPlayer {
        return mediaPlayer
    }

    /**
     * Release the media player component and the associated native media player resources.
     */
    fun release() {
//        onBeforeRelease()

        // It is safe to remove listeners like this even if none were added (depends on configured InputEvents in the
        // constructor)
//        if (videoSurfaceComponent != null) {
//            videoSurfaceComponent.removeMouseListener(this)
//            videoSurfaceComponent.removeMouseMotionListener(this)
//            videoSurfaceComponent.removeMouseWheelListener(this)
//            videoSurfaceComponent.removeKeyListener(this)
//        }

        mediaPlayer.release()

        if (ownFactory) {
            mediaPlayerFactory.release()
        }

//        onAfterRelease()
    }

    override fun mediaPlayerFactory(): MediaPlayerFactory {
        return mediaPlayerFactory
    }

    /**
     * Default implementation of a video surface component that uses a [CallbackImagePainter] to render the video
     * image.
     */
    private inner class DefaultVideoSurfaceComponent {
        init {
            // Set a reasonable default size for the video surface component in case the client application does
            // something like using pack() rather than setting a specific size
            preferredSize = IntSize(640, 360)
        }
    }

    /**
     * Default implementation of a buffer format callback that returns a buffer format suitable for rendering into a
     * [BufferedImage].
     */
    private inner class DefaultBufferFormatCallback : BufferFormatCallbackAdapter() {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            newVideoBuffer(sourceWidth, sourceHeight)
            return RV32BufferFormat(sourceWidth, sourceHeight)
        }
    }

    private var preferredSize: IntSize = IntSize.Zero

    /**
     * Used when the default buffer format callback is invoked to setup a new video buffer.
     *
     *
     * Here we create a new image to match the video size, and set the data buffer within that image as the data buffer
     * in the [DefaultRenderCallback].
     *
     *
     * We also set a new preferred size on the video surface component in case the client application invalidates their
     * layout in anticipation of re-sizing their own window to accommodate the new video size.
     *
     * @param width width of the video
     * @param height height of the video
     */
    private fun newVideoBuffer(width: Int, height: Int) {
        image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        defaultRenderCallback!!.setImageBuffer(image!!)
        if (videoSurfaceComponent != null) {
            preferredSize = IntSize(width, height)
        }
    }

    var composeImage: ImageBitmap by mutableStateOf(ImageBitmap(128, 128))

    /**
     * Default implementation of a render callback that copies video frame data directly to the data buffer of an image
     * raster.
     */
    private inner class DefaultRenderCallback : RenderCallbackAdapter() {
        fun setImageBuffer(image: BufferedImage) {
            setBuffer((image.raster.dataBuffer as DataBufferInt).data)
        }

        override fun onDisplay(mediaPlayer: MediaPlayer, buffer: IntArray) {
            image?.let {
                composeImage = it.toComposeImageBitmap()
            }
//            videoSurfaceComponent!!.repaint()
        }
    }

    /**
     * Template methods to make it easy for a client application sub-class to render a lightweight overlay on top of the
     * video.
     *
     *
     * When this method is invoked the graphics context will already have a proper scaling applied according to the
     * video size.
     *
     * @param g2 graphics drawing context
     */
    protected fun onPaintOverlay(g2: Graphics2D?) {
    }

    companion object {
        /**
         * Default factory initialisation arguments.
         */
        val DEFAULT_FACTORY_ARGUMENTS: Array<String> = arrayOf(
            "--video-title=vlcj video output",
            "--no-snapshot-preview",
            "--quiet",
            "--intf=dummy",
            "--no-hotkeys",
        )

    }
}
