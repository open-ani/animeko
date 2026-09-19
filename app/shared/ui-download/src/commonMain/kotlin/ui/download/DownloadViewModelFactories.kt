/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import me.him188.ani.app.ui.download.subject.SubjectDownloadsPresenterFactory
import me.him188.ani.app.ui.download.subject.SubjectDownloadsViewModel
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

fun createDownloadManagementViewModel(): DownloadManagementViewModel {
    val koin = KoinPlatform.getKoin()
    return DownloadManagementViewModel(koin.get(), koin.get(), koin.get(), koin.get(), subjectDownloadsPresenterFactory(koin))
}

fun createSubjectDownloadsViewModel(subjectId: Int): SubjectDownloadsViewModel {
    val koin = KoinPlatform.getKoin()
    return SubjectDownloadsViewModel(subjectId, subjectDownloadsPresenterFactory(koin))
}

private fun subjectDownloadsPresenterFactory(koin: Koin) = SubjectDownloadsPresenterFactory(
    koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(),
)
