# 播放器截图

播放器全屏时, 右侧浮动按钮栏有一个截图按钮 (`ScreenshotButton`). 点击后截取当前视频帧保存到相册或图片目录, 并在播放器内给出反馈.
代码在 `app/shared/video-player` 的 `screenshot` (平台能力) 与 `ui/screenshot` (反馈 UI) 两个包, 调用方是 `EpisodePage`.

## 流程

1. `EpisodePage` 用 `playerScreenshotFileName` 生成文件名 `条目ID-剧集序号-XmYsZms.png`, 调用 `PlayerScreenshotCapturer.capture`. 上一张还在保存时忽略再次点击.
2. 截图器先确保权限 (Android 9 及以下申请 WRITE_EXTERNAL_STORAGE; Android 10 起和桌面都不需要), 然后抓帧并保存.
   失败不抛异常, 以 `PlayerScreenshotResult.Failure(reason)` 返回: 权限被拒 / 不支持 / 没有画面 / 保存失败.
3. 成功时 `PlayerScreenshotPanelState.present` 交给 `PlayerScreenshotOverlay`:
   播放器区域闪光一次; 截图原位停留 200ms 后以容器变换收进角落, 竖屏布局 (区域高大于宽) 右下, 横屏布局左下;
   落定后长出面板: 画面所在的 A 区域 (圆角矩形, 画面四周留边) 下方挂着药丸形的 B 区域 (外侧边与 A 对齐, 向屏幕中央伸出,
   装分享与复制两个圆形图标按钮), 由 `screenshotPanelOutline` 描成一条闭合路径 (A 的内侧边与 B 的上边以内凹圆角相接),
   做成 Shape 后填 surfaceContainer 并带阴影, 按钮圆底为 surfaceContainerLowest; 没有关闭按钮, 6 秒后自动收起,
   鼠标悬停或按住时暂停计时; 点击画面用应用内的图片查看器打开它并立即收起面板.
   失败时 toast 说明原因.
4. 面板动作由 `PlayerScreenshotSharer` 实现: 分享按钮调用 `share`, 复制按钮调用 `copy`; 点击画面走 `ImageViewerHandler.viewImage`.

## 平台实现

| 平台 | 抓帧 | 保存位置 | 分享 | 复制 |
|------|------|----------|------|------|
| Android | `PixelCopy` 读取 ExoPlayer 的 SurfaceView, 只含视频帧, 不含弹幕与字幕 | MediaStore `Pictures/Animeko`; Android 9 及以下直接写公共图片目录, 经 FileProvider 的 `external-path` 共享 | `ACTION_SEND` 系统分享面板 | 截图的 content URI 放进剪贴板 |
| 桌面 | mediamp 的 `Screenshots` feature. mpv 后端从自己的 surface ring 读回, 并按 `osd-dimensions` 裁掉黑边 | `~/Pictures/Animeko`; 不可用时应用数据目录下的 `screenshots` | 文件管理器中定位文件 | 图片与文件一起放进剪贴板 |
| iOS | 不支持: AVKit 后端没有读取当前帧的能力, 按钮不显示 | - | - | - |

点击画面在三个平台都用应用内图片查看器打开: Android 传 content URI, 桌面传绝对路径, 都是 Sketch 支持的模型.

`rememberPlayerScreenshotCapturer()` 与 `rememberPlayerScreenshotSharer()` 是 expect/actual, 各平台在此提供实现.

## 层级与布局

`VideoScaffold` 的 `screenshotOverlay` 槽覆盖整个播放器区域, 位于控制器之上、侧边栏之下, 面板以外不拦截输入.
槽参数是底部控制栏当前占用的高度 (隐藏时为 0): 面板据此避让, 控制栏隐藏时面板下移到边距处.
容器变换的起点与视频区域对齐, 不应用系统栏边距; 只有面板的停靠位置避开边距.

面板几何由纯函数 `computePlayerScreenshotPanelGeometry` 计算 (`PlayerScreenshotPanelLayoutTest`);
闪光、变换、按钮与自动收起由 `PlayerScreenshotOverlayTest` 用合成时钟覆盖.
