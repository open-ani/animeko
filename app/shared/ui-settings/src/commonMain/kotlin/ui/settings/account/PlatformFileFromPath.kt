package me.him188.ani.app.ui.settings.account

import io.github.vinceglb.filekit.PlatformFile
import kotlinx.io.files.Path

expect fun platformFileFromPath(path: Path): PlatformFile?
