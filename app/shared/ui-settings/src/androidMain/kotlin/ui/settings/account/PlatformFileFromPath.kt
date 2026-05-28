package me.him188.ani.app.ui.settings.account

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path

actual fun platformFileFromPath(path: Path): PlatformFile? = PlatformFile(path)
