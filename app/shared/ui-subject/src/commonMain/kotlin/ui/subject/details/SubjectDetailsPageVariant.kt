/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImagePainter
import com.kmpalette.palette.graphics.Palette
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeRequest
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.models.subject.Tag
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.subject.details.layout.SubjectDetailsLayoutParams
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsState

/**
 * 条目详情页变体: 应用入口可提供一个替代布局 (如遥控器形态的单列信息流:
 * Hero 首屏 + 横向区块).
 *
 * 只有 [ThemeSettings.tvImmersiveDetails][me.him188.ani.app.data.models.preference.ThemeSettings.tvImmersiveDetails]
 * 开启时才生效, 关闭则回退默认多栏布局.
 *
 * 变体自带 info 加载占位 (调用方不等 info 加载完就进入, 避免先闪默认布局再整页切换).
 */
fun interface SubjectDetailsPageVariant {
    @Composable
    fun Page(
        state: SubjectDetailsState,
        selfInfo: SelfInfoUiState,
        layoutParams: SubjectDetailsLayoutParams,
        onPlay: (episodeId: Int) -> Unit,
        onClickTag: (Tag) -> Unit,
        onClickLogin: () -> Unit,
        onShowComments: () -> Unit,
        modifier: Modifier,
        onEpisodeCollectionUpdate: (SetEpisodeCollectionTypeRequest) -> Unit,
        showTopBar: Boolean,
        windowInsets: WindowInsets,
        backgroundPalette: Palette?,
        onClickOpenExternal: () -> Unit,
        onCoverImageSuccess: (AsyncImagePainter.State.Success) -> Unit,
        onClickCache: (() -> Unit)?,
        /**
         * 视频背景模式 (播放器内嵌): 页面底色透明, 不放渐变/TMDB 背景图,
         * 改为对下层视频画遮罩 —— 首屏只压底部, 滚动后整屏变暗.
         */
        videoBackground: Boolean,
        /** 内嵌变体介绍页顶部按上键的回调 (回到播放器选集条); null 不处理. */
        onVideoBackgroundExitUp: (() -> Unit)?,
    )

    /**
     * [SubjectDetailsState] 还没构造出来时的首屏占位 (加载器先发 `Placeholder` 状态,
     * 首次发射到达才有 state).
     *
     * 这一段过去是**居中转圈的空白页**, 于是点一张卡要看三段先后到达的画面: 转圈 ->
     * 整页换成真布局 -> 背景图再淡进来. 变体自绘的意义是让这一帧**已经长得像目标页**:
     * 手上有的东西 (导航带来的标题 + 进程内已解析的背景图) 先按目标页的版式画出来,
     * 真布局到达时这些部分原地不动, 只有其余内容补上去.
     *
     * @param subjectInfo 导航占位信息 (标题/封面), 可能为 null (刚进页、还没有任何数据).
     */
    @Composable
    fun LoadingPlaceholder(
        subjectInfo: SubjectInfo?,
        layoutParams: SubjectDetailsLayoutParams,
        modifier: Modifier,
        windowInsets: WindowInsets,
    ) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

val LocalSubjectDetailsPageVariant = staticCompositionLocalOf<SubjectDetailsPageVariant?> { null }
