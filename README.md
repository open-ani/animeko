<div align="center">

![Animeko](https://socialify.git.ci/open-ani/animeko/image?description=1&descriptionEditable=%E9%9B%86%E6%89%BE%E7%95%AA%E3%80%81%E8%BF%BD%E7%95%AA%E3%80%81%E7%9C%8B%E7%95%AA%E7%9A%84%E4%B8%80%E7%AB%99%E5%BC%8F%E5%BC%B9%E5%B9%95%E8%BF%BD%E7%95%AA%E5%B9%B3%E5%8F%B0&font=Jost&logo=https%3A%2F%2Fraw.githubusercontent.com%2Fopen-ani%2Fanimeko%2Frefs%2Fheads%2Fmain%2F.github%2Fassets%2Flogo.png&name=1&owner=1&pattern=Plus&theme=Light)

| 正式版                                                                                                                                                                          | 测试版                                                                                                                                                                                     | 讨论群                                                                                                                                                                                                                                                                                                                                                                                                           |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [![Stable](https://img.shields.io/github/release/open-ani/ani.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/open-ani/ani/releases/latest) | [![Beta](https://img.shields.io/github/v/release/open-ani/ani.svg?maxAge=3600&label=Beta&labelColor=2c2c47&color=1c1c39&include_prereleases)](https://github.com/open-ani/ani/releases) | [![Group](https://img.shields.io/badge/Telegram-2CA5E0?style=flat-squeare&logo=telegram&logoColor=white)](https://t.me/openani) |

</div>

[dmhy]: http://www.dmhy.org/

[Bangumi]: http://bangumi.tv

[ddplay]: https://www.dandanplay.com/

[Compose Multiplatform]: https://www.jetbrains.com/compose-multiplatform/

[acg.rip]: https://acg.rip

[Mikan]: https://mikanani.me/

[Ikaros]: https://ikaros.run/

[Kotlin Multiplatform]: https://kotlinlang.org/docs/multiplatform.html

[ExoPlayer]: https://developer.android.com/media/media3/exoplayer

[VLC]: https://www.videolan.org/vlc/

[libtorrent]: https://libtorrent.org/

Animeko 支持云同步观看记录 ([Bangumi][Bangumi])、多视频数据源、缓存、弹幕、以及更多功能，提供尽可能简单且舒适的追番体验。

> Animeko 曾用名 Ani，现在也简称 Ani。

[立即下载](https://animeko.org/)

https://github.com/user-attachments/assets/e63636c9-30b7-411c-aa6b-e5b78b900726

## 主要功能

### 浏览来自 [Bangumi][Bangumi] 的番剧信息以及社区评价

| <img src=".readme/images/features/subject-details.png" alt="" width="200"/> | <img src=".readme/images/features/subject-rating.png" alt="" width="200"/> | 
|:---------------------------------------------------------------------------:|:--------------------------------------------------------------------------:|

### 丰富的检索方式：新番时间表、标签搜索

除遥控器焦点适配外，大部分页面针对电视大屏**专门重新设计**（欢迎提 issue 或邮件反馈）。其中探索页、详情页与新番时间表可以在设置 → 主题里单独切回上游原布局（低端机可关）：

| <img src=".readme/images/features/anime-schedule.png" alt="" width="200"/> | <img src=".readme/images/features/search-by-tag.png" alt="" width="200"/> | 
|:--------------------------------------------------------------------------:|:-------------------------------------------------------------------------:|

### 云同步追番进度

- 省心的追番进度管理，看完视频自动更新进度
- 打开 APP 立即继续观看，无需回想上次看到了哪

| <img src=".readme/images/features/subject-collection.png" alt="" width="200"/> | <img src=".readme/images/features/home.png" alt="" width="200"/> | 
|:------------------------------------------------------------------------------:|:----------------------------------------------------------------:|

### 聚合数据源

- [聚合视频数据源](https://github.com/creamycake-anime/ani-subs)，全自动选择
  > 还支持 BitTorrent、Jellyfin、Emby、以及自定义源
- 聚合全网弹幕源（[弹弹play][ddplay]），以及 Animeko 自己的[弹幕服务](https://danmaku-cn.myani.org/swagger/index.html)

| <img src=".readme/images/features/mediaselector-simple.png" alt="" width="200"/> | <img src=".readme/images/features/mediaselector-detailed.png" alt="" width="200"/> |
|:--------------------------------------------------------------------------------:|:----------------------------------------------------------------------------------:|

| <img src=".readme/images/features/episode.png" alt="" width="200"/> | <img src=".readme/images/features/episode-scrolled.png" alt="" width="200"/> |
|:-------------------------------------------------------------------:|:----------------------------------------------------------------------------:|

### 离线缓存

- 所有数据源都能缓存

| <img src=".readme/images/features/cache-episode.png" alt="" width="200"/> | <img src=".readme/images/features/cache-list.png" alt="" width="200"/> |
|:-------------------------------------------------------------------------:|:----------------------------------------------------------------------:|

### 精美界面

| <img src=".readme/images/features/player-fullscreen.png" alt="" width="600"/> |
|:-----------------------------------------------------------------------------:|

- 适配平板和大屏设备

| <img src=".readme/images/features/pc-home.png" alt="" width="600"/> |
|:-------------------------------------------------------------------:|

| <img src=".readme/images/features/pc-search.png" alt="" width="600"/> |
|:---------------------------------------------------------------------:|

| <img src=".readme/images/features/pc-search-detail.png" alt="" width="600"/> |
|:----------------------------------------------------------------------------:|

### 更多个性设置

| <img src=".readme/images/features/danmaku-settings.png" alt="" width="600"/> |
|:----------------------------------------------------------------------------:|

| <img src=".readme/images/features/theme-settings.png" alt="" width="200"/> | <img src=".readme/images/features/media-preferences.png" alt="" width="200"/> |
|:--------------------------------------------------------------------------:|:-----------------------------------------------------------------------------:|

## 📺 Android TV

| 平台          | 下载                                                                                        |
|-------------|-------------------------------------------------------------------------------------------|
| Android / 电视 | [前往 Releases 下载最新版 APK](https://github.com/GrahamZen/animeko/releases/latest) |

> Release 里按架构分包：几乎所有电视与电视盒子选 `arm64-v8a`，旧盒子选 `armeabi-v7a`，模拟器与 x86 盒子选 `x86_64`——体积更小、更省存储。不确定或装不上时再用 `universal`（包含全部架构，体积最大）。同一个 APK 同时适用于手机、平板和电视盒子。

> 也可前往专门的下载分发仓库 [**GrahamZen/animeko-tv-releases**](https://github.com/GrahamZen/animeko-tv-releases/releases/latest)（每日自动镜像本仓库 Release）。

### ⚠️ 系统版本要求

- **推荐 Android 11（API 30）及以上**。
- 在 **Android 10 及以下** 的系统里很可能无法正确请求焦点，导致遥控器功能不可用。遇到遥控器无法操作的问题时，请先确认系统版本。
- NVIDIA Shield 等主流电视盒子的较新系统一般可正常使用。

### 遥控器使用说明

播放器分三层，**返回键逐层往回**：纯视频 → 控制层（进度条与下方按钮行）→ 面板 / 侧边栏（数据源、选集、弹幕设置）。

画面上什么都没有时（纯视频）：

| 按键              | 效果                                                            |
|-----------------|---------------------------------------------------------------|
| 上 / 下键          | 唤出控制层，焦点落在进度条上                                                |
| 确认键（短按）         | 播放 / 暂停（暂停时顺带唤出控制层）                                           |
| 确认键（按住）         | 长按倍速，松手还原（倍率取设置里的「长按倍速倍率」，默认 2.5x）                            |
| 左 / 右键（单按）      | 快退 / 快进 5 秒，中央给一次图标反馈，不唤出控制层                                  |
| 左 / 右键（连按或按住）   | 进入拖动预览：出进度条与缩略图，越按越快；确认键跳到圆点处继续播放，返回键取消                       |
| 播放 / 暂停键        | 播放 / 暂停                                                       |
| 快进 / 快退键        | 下一集 / 上一集                                                     |

控制层里方向键就是普通的焦点移动（进度条、胶囊行、图标行、选集条之间）。

焦点丢失不用手动找回：每个页面都记着上次聚焦的那张卡 / 那颗按钮，焦点悬空时会自动落回去。

设置 → 数据源管理支持纯遥控器排序：在某一行上**长按确认键**出菜单 → 选「多选」进入多选模式 → 在多选模式里**长按**任意一行出批量菜单 → 选「排序」把这一行拿起来，上下键移动它，确认键或返回键放下即保存。多选模式下按返回键退出多选。

> 最开始的登录部分如果焦点丢失，请接入鼠标完成登录，之后即可继续使用遥控器。

### 手动完成验证码页面的操作

| 操作              | 效果                                  |
|-----------------|-------------------------------------|
| 方向键             | 移动光标（先按一下方向键唤出光标）          |
| 确认键（短按）        | 在光标位置模拟触摸点击（用于通过验证码）      |
| 确认键（长按 ~500ms） | 点击"✓"（大部分情况通过验证码后会自动关闭）  |
| 返回键             | 取消，关闭对话框                          |

### 已知问题

- 在 Android 10 及以下系统中可能无法正确请求焦点，从而无法使用遥控器功能（见上方系统版本要求）。

## 下载

Animeko 支持所有主流平台：Android、iOS、Windows、macOS、Linux。

- 稳定版本: 功能稳定  
  [下载稳定版本](https://animeko.org/downloads/)

通常建议使用稳定版本. 如果你愿意参与测试并拥有一定的对 bug 的处理能力, 也欢迎使用测试版本更快体验新功能.
具体版本类型可查看下方.

- 测试版本: 体验最新功能  
  [下载测试版本](https://animeko.org/downloads/)

## 技术总览

如果你是开发者，我们总是欢迎你提交 PR 参与开发！
以下几点可以给你一个技术上的大概了解。

- [Kotlin 多平台][Kotlin Multiplatform]架构；
- 使用新一代响应式 UI 框架 [Compose Multiplatform][Compose Multiplatform] 构建
  UI；
- 内置专为 Animeko 打造的“基于 [libtorrent][libtorrent] 的 BitTorrent 引擎，优化边下边播的体验；
- 高性能弹幕引擎，公益弹幕服务器 + 网络弹幕源；
- 适配多平台的[视频播放器](https://github.com/open-ani/mediamp)，Android 底层为 [ExoPlayer][ExoPlayer]，PC 底层为 [VLC][VLC]；
- 多类型数据源适配，内置 [动漫花园][dmhy]、[Mikan]，拥有强大的自定义数据源编辑器和自动数据源选择器。

### 参与开发

欢迎你提交 PR 参与开发，
有关项目技术细节请参考 [CONTRIBUTING](docs/contributing/README.md)。

## FAQ

### 资源来源是什么?

全部视频数据都来自网络, Animeko 本身不存储任何视频数据。
Animeko 支持两大数据源类型：BT 和在线。BT 源即为公共 BitTorrent P2P 网络，
每个在 BT
网络上的人都可分享自己拥有的资源供他人下载。在线源即为其他视频资源网站分享的内容，目前使用 [creamycake ani-subs](https://github.com/creamycake-anime/ani-subs)。Animeko
本身并不提供任何视频资源。

本着互助精神，使用 BT 源时 Animeko 会自动做种 (分享数据)。
BT 指纹为 `-AL4123-`，其中 `4123` 为版本号 `4.12.3`；UA 为类似 `ani_libtorrent/4.12.3`。

### 弹幕来源是什么?

Animeko 拥有自己的公益弹幕服务器，在 Animeko 应用内发送的弹幕将会发送到弹幕服务器上。每条弹幕都会以
Bangumi
用户名绑定以防滥用（并考虑未来增加举报和屏蔽功能）。

Animeko 还会从[弹弹play][ddplay]获取关联弹幕，弹弹play还会从其他弹幕平台例如哔哩哔哩港澳台和巴哈姆特获取弹幕。
番剧每集可拥有几十到几千条不等的弹幕量。
