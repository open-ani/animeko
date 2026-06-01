/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android.tv

import android.content.Intent
import android.net.Uri
import android.service.dreams.DreamService
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import okio.Path.Companion.toPath
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.android.activity.UiScaleMirror
import me.him188.ani.android.activity.withUiScale
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.createDefaultImageLoader
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.renderSubjectSeason
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.context.GlobalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import java.lang.Math.floorMod

/**
 * TV 系统屏保 (DreamService): 轮播在看 + 热门动画的 TMDB 横版剧照.
 *
 * - 数据: 在看列表 + 最近热门, TMDB 搜不到剧照的动画直接跳过;
 *   每个动画取全部 backdrop, 轮到它时随机放一张.
 * - 交互 (isInteractive): 确定键 → deep link 打开当前动画详情页并退出屏保;
 *   左/右键 → 手动切上一个/下一个动画; 其他任意键 → 正常退出屏保.
 * - 需要用户在系统设置 → 屏保 里选择 Animeko (代码无法自动设为默认).
 *
 * 跑在主进程, Koin/TMDB 缓存/coil 磁盘缓存全部复用; ComposeView 需要手动挂
 * lifecycle owner (DreamService 不是 LifecycleOwner), 见 [DreamLifecycleOwner].
 */
class AniDreamService : DreamService() {
    private val logger = logger<AniDreamService>()

    private var scope: CoroutineScope? = null
    private var dreamLifecycleOwner: DreamLifecycleOwner? = null
    private var imageLoader: ImageLoader? = null

    /** 已加载的动画列表 (compose 与按键处理共同读取). */
    private val showsState = mutableStateOf(emptyList<DreamShow>())

    /** 当前轮到第几个动画 (可为任意整数, 读取时对列表长度取模). */
    private val currentIndex = mutableIntStateOf(0)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true

        val owner = DreamLifecycleOwner().also { dreamLifecycleOwner = it }
        owner.onCreate()

        val koin = GlobalContext.get()
        val loader = createDefaultImageLoader(
            this,
            koin.get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            // 与主应用共用同一磁盘缓存目录: 屏保加载过的 backdrop 主应用直接命中
            diskCacheDirectory = cacheDir.resolve("coil3_disk_cache").absolutePath.toPath(),
        ).also { imageLoader = it }

        val serviceScope = MainScope().also { scope = it }
        serviceScope.launch {
            runCatching { loadShows() }
                .onSuccess {
                    logger.info { "Dream shows loaded: ${it.size}" }
                    showsState.value = it
                }
                .onFailure { logger.error(it) { "Failed to load dream shows" } }
        }

        // ComposeView 沿 view tree 向上找 lifecycle owner, 挂在 decorView 上,
        // 必须在 setContentView 之前设置
        window.decorView.setViewTreeLifecycleOwner(owner)
        window.decorView.setViewTreeSavedStateRegistryOwner(owner)
        setContentView(
            // 屏保是独立于 MainActivity 的 window, 拿不到那边的界面缩放; 从镜像自己读一份,
            // 否则在 densityDpi 上报不准的电视上, 主界面已经校正、屏保仍然偏小 (见 UiScaleApplier)
            ComposeView(withUiScale(UiScaleMirror.read(this))).apply {
                setContent {
                    DreamContent(loader)
                }
            },
        )
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        imageLoader?.shutdown()
        imageLoader = null
        dreamLifecycleOwner?.onDestroy()
        dreamLifecycleOwner = null
        super.onDetachedFromWindow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) openCurrentSubject()
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) currentIndex.intValue++
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN) currentIndex.intValue--
                true
            }

            else -> {
                // 返回/上下等其他按键: 正常退出屏保
                if (event.action == KeyEvent.ACTION_UP) finish()
                true
            }
        }
    }

    private fun currentShowOrNull(): DreamShow? {
        val shows = showsState.value
        if (shows.isEmpty()) return null
        return shows[floorMod(currentIndex.intValue, shows.size)]
    }

    private fun openCurrentSubject() {
        val show = currentShowOrNull() ?: run { finish(); return }
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("ani://subjects/${show.subjectId}"))
                    .setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { logger.error(it) { "Failed to open subject ${show.subjectId} from dream" } }
        finish()
    }

    /**
     * 在看 (优先) + 热门, 按 subjectId 去重; 每个条目取 TMDB 全部剧照, 取不到的跳过.
     * 顺序打乱, 每次屏保激活的轮播顺序都不同.
     */
    private suspend fun loadShows(): List<DreamShow> {
        val koin = GlobalContext.get()
        val tmdb = koin.get<TmdbImageService>()
        val collectionRepo = koin.get<SubjectCollectionRepository>()

        val candidates = LinkedHashMap<Int, SubjectCollectionInfo>()
        runCatching {
            collectionRepo
                .mostRecentlyUpdatedSubjectCollectionsFlow(WATCHING_LIMIT, listOf(UnifiedCollectionType.DOING))
                .first()
        }.getOrElse { emptyList() }.forEach { candidates[it.subjectId] = it }

        val trending = runCatching {
            koin.get<TrendsRepository>().getTrendsInfo().subjects.take(TRENDING_LIMIT)
        }.getOrElse { emptyList() }
        for (item in trending) {
            if (item.bangumiId in candidates) continue
            // 非收藏条目仓库也会从服务器拉全量数据 (原名/评分/连载/进度), 与在看条目同构
            val info = runCatching {
                withTimeoutOrNull(SUBJECT_FETCH_TIMEOUT_MILLIS) {
                    collectionRepo.subjectCollectionFlow(item.bangumiId).first()
                }
            }.getOrNull() ?: continue
            candidates[item.bangumiId] = info
        }

        return coroutineScope {
            candidates.values.map { info ->
                async {
                    val backdrops = runCatching {
                        tmdb.getAllBackdropUrls(
                            info.subjectId,
                            info.subjectInfo.name,
                            // 新番刚播时 TMDB 常常还没有剧照, 负缓存据此限期失效而非永久
                            activeAsOfDate = info.episodes.newestAiredDateStringOrNull(),
                        )
                    }.getOrElse { emptyList() }
                    if (backdrops.isEmpty()) null // TMDB 搜不到剧照, 跳过该动画
                    else DreamShow(info, backdrops)
                }
            }.awaitAll().filterNotNull().shuffled()
        }
    }

    @Composable
    private fun DreamContent(loader: ImageLoader) {
        val shows by showsState
        val index by currentIndex

        // 自动轮播
        LaunchedEffect(shows) {
            if (shows.isEmpty()) return@LaunchedEffect
            while (true) {
                delay(SLIDE_INTERVAL_MILLIS)
                currentIndex.intValue++
            }
        }

        // 轮到某个动画时随机抽一张剧照; remember 按 (列表, 序号) 缓存, 重组不重抽
        val slide = remember(shows, index) {
            if (shows.isEmpty()) null
            else {
                val show = shows[floorMod(index, shows.size)]
                Slide(show, show.backdrops.random())
            }
        }

        // 深色主题包住整个内容: 复用的 AiringLabel / SubjectRatingSummary 取
        // MaterialTheme 颜色, 默认亮色主题在黑底剧照上会看不清
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Crossfade(
                    targetState = slide,
                    animationSpec = tween(CROSSFADE_MILLIS),
                    label = "dreamSlide",
                ) { current ->
                    if (current == null) return@Crossfade
                    val subject = current.show.collection.subjectInfo
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = current.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            imageLoader = loader,
                            contentScale = ContentScale.Crop,
                        )
                        // 底部渐变遮罩, 保证文字可读
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    ),
                                ),
                        )
                        // 所有文字统一白色 + 黑色投影, 不依赖遮罩也保持可读
                        val textShadow = remember {
                            Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), blurRadius = 8f)
                        }
                        Column(
                            Modifier.align(Alignment.BottomStart).padding(horizontal = 56.dp, vertical = 40.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                subject.displayName,
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(shadow = textShadow),
                            )
                            // 日语原名 (与显示名相同时不重复)
                            if (subject.name.isNotBlank() && subject.name != subject.displayName) {
                                Text(
                                    subject.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(shadow = textShadow),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                val seasonText = renderSubjectSeason(subject.airDate)
                                if (seasonText.isNotBlank()) {
                                    Text(
                                        seasonText,
                                        style = MaterialTheme.typography.titleSmall.copy(shadow = textShadow),
                                        color = Color.White,
                                        maxLines = 1,
                                    )
                                }
                                val airingLabelState = remember(current.show) {
                                    AiringLabelState(
                                        stateOf(current.show.collection.airingInfo),
                                        stateOf(current.show.collection.progressInfo),
                                    )
                                }
                                // 保留详情页的双色层次: 进度部分 (连载至第 x 话) 纯白强调,
                                // 其余 (全 y 话等) 降到 75% 白 —— 有投影托底, 两档都可读
                                AiringLabel(
                                    airingLabelState,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White.copy(alpha = 0.75f),
                                        shadow = textShadow,
                                    ),
                                    progressColor = Color.White,
                                )
                            }
                        }
                        Text(
                            "按「确定」查看详情",
                            color = Color.White,
                            fontSize = 14.sp,
                            style = TextStyle(shadow = textShadow),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 56.dp, vertical = 40.dp),
                        )
                    }
                }
            }
        }
    }

    private class Slide(val show: DreamShow, val imageUrl: String) {
        // Crossfade 用 equals 判断是否需要过渡, 同图不重放动画
        override fun equals(other: Any?): Boolean =
            other is Slide && other.show.subjectId == show.subjectId && other.imageUrl == imageUrl

        override fun hashCode(): Int = 31 * show.subjectId + imageUrl.hashCode()
    }

    private class DreamShow(
        val collection: SubjectCollectionInfo,
        val backdrops: List<String>,
    ) {
        val subjectId: Int get() = collection.subjectId
    }

    /**
     * DreamService 不是 LifecycleOwner, ComposeView 又必须从 view tree 里拿到
     * lifecycle / savedState owner —— 手动实现一个跟随屏保窗口生命周期的假 owner.
     */
    private class DreamLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private companion object {
        private const val WATCHING_LIMIT = 10
        private const val TRENDING_LIMIT = 15
        private const val SLIDE_INTERVAL_MILLIS = 12_000L
        private const val CROSSFADE_MILLIS = 800
        private const val SUBJECT_FETCH_TIMEOUT_MILLIS = 15_000L
    }
}
