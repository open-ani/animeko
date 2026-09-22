# PR 3466 danmaku-host review verification

Tested commit: `64a630c8d714ed36f679aabf35c0eb277ec6b28b`.

- Android: Pixel 8 Pro emulator, Android 15 / API 35, ARM64, SwiftShader. Inline playback displays danmaku; Home enters a video-only PiP window; returning to the app displays danmaku again. Screenshots cover those states. The return screenshot is from a repeat transition after seeking to a comment-rich segment before entering PiP.
- Desktop: packaged macOS ARM64 executable built and launched with the desktop-ui-verify skill. Playback and danmaku rendering verified, including seeking.
- Video: the same locally served 960x540 H.264/AAC test pattern used in the earlier report, loaded through the real web-selector source and player. Danmaku comes from the selected episode's normal sources.

Validation:

- `:app:shared:compileKotlinDesktop` succeeded.
- `:app:android:installDefaultDebug -Pani.android.abis=arm64-v8a` succeeded.
- `:app:desktop:createDistributable` succeeded.
- `:app:shared:desktopTest --tests me.him188.ani.app.ui.subject.episode.EpisodeVideoControllerTest`: 49 passed, 2 existing skipped, 0 failures. Includes the regression asserting PiP omits the danmaku slot, fills the available viewport, and restores the slot after exit.
- `git diff --check` passed.

This verification covers the danmaku-host change. The earlier iOS simulator initialization crash remains outside this change.
