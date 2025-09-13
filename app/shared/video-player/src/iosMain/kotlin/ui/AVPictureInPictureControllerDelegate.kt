package me.him188.ani.app.videoplayer.ui


import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.play
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.sel_registerName

internal class AVPictureInPictureControllerDelegate : NSObject(), AVPictureInPictureControllerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun pictureInPictureControllerWillStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        NSLog("Picture-in-Picture will start")
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication().performSelector(
                sel_registerName("suspend")
            )
            pictureInPictureController.playerLayer.player?.play()
        }
    }

    override fun pictureInPictureControllerDidStartPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        NSLog("Picture-in-Picture did start")


    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        failedToStartPictureInPictureWithError: NSError
    ) {
        NSLog("Picture-in-Picture failed to start: ${failedToStartPictureInPictureWithError.localizedDescription}")

    }

    override fun pictureInPictureControllerWillStopPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        NSLog("Picture-in-Picture will stop")
    }

    override fun pictureInPictureControllerDidStopPictureInPicture(
        pictureInPictureController: AVPictureInPictureController
    ) {
        NSLog("Picture-in-Picture did stop")
//        pictureInPictureController.playerLayer.removeFromSuperlayer()
    }

    override fun pictureInPictureController(
        pictureInPictureController: AVPictureInPictureController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit
    ) {
        NSLog("Picture-in-Picture restore user interface requested")
        // 完成恢复
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
    }
}