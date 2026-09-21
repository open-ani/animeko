# Google TV 首页交互参考

## 样本

2026-09-20 从用户已启动的 Google TV API 36 模拟器提取：
`com.google.android.apps.tv.launcherx`，版本 `1.0.877433387`（772000），x86_64。

APK SHA-256：
`8F2A17860E349F5213DC81AD3AEECC1439C0FCA7ECEFC36557837A3D908EFD36`。
v3 签名验证通过，证书主体为 Google Inc. 的 `android-tv-launcherx`。

APK 和签名记录位于 `build/extracted-apks/google-tv-20260920/`。
JADX 1.4.7 输出位于 `build/reports/google-tv-home-analysis/decompiled/`。
19,995 个类中有 47 个反编译错误；以下记录使用可读方法、XML 资源、
运行时视图树和实际遥控器导航相互核对。混淆名称仅用于定位这一版本。

## 结构

当前 `HomeActivity` 使用 `myw` 的轮播，根节点 `0x7f0b055c`。
`wjh` 绑定主纵向网格 `0x7f0b0653` 和独立 immersive 预览容器
`0x7f0b0730 / 0x7f0b0731`。
`wjg.a()` 根据选中行的 `wbn.c()` 更新预览；无预览提供者时隐藏。
APK 中的 `ggd` 是另一个轮播实现，不能混用其尺寸推导当前页面。

## 可直接定位的参数

| 定位 | 结果 |
| --- | --- |
| `ocg.a()` 分支 4 | standard_immersive_card 宽 153dp；16:9 后取整高 86dp |
| `ocg.a()` 分支 2 → `prr.ar()` | standard_image_card_4_up 宽 196dp，高 110dp |
| `wtk.b(width)` | 宽 153 的圆角 12dp；宽 196 的圆角 16dp |
| `wqx.L(width, ...)` | 153 卡片缩放 1.033；196 卡片缩放 1.08；描边 2.5dp、间隔 2dp |
| `myf` 分支 11 → `myo` → `myx.b` | ItemTransformFacet 后置额外空间，资源 `0x7f070509` = 147dp |
| `wye.g()`、`wye.e()` | 250ms ValueAnimator，取消旧动画，从当前位置继续；默认 accelerate/decelerate |
| `wkj` → `wye.l()` | 行强调 fraction `0x7f0a0000 / 0073 / 0005` = 0.2 / 1 / 0.6 |
| layout `0x7f0e0137` | 轮播标题宽 560dp、最多两行、无 font padding；标题/附加信息/简介间距 12dp；简介最大宽 484dp、两行；操作顶部间距 24dp |
| `AutoResizeTextView.e()` + array `0x7f03000d` | 46、44、42、40、38、36dp；从大到小尝试单行，最小字号允许两行；仅最小字号能单行时将宽度乘 0.58 |
| layout `0x7f0e01d9` + arrays `0x7f03001f / 0020` | 沉浸预览标题宽 620dp，字号从 50 到 36dp、步长 2；最多两行，最小字号宽度系数 1 |
| `nkx` / `nky.c()` | 沉浸预览独立绑定标题、来源、简介、评分等元信息与标签；空字段隐藏对应视图 |
| style `0x7f150b75` | 操作按钮高 40dp，水平内边距 20dp |
| animator `0x7f02005f` | 聚焦缩放 1.1 / 500ms；按下 1.05、alpha 0.7 / 150ms；失焦仅 alpha 淡至 0 / 200ms |
| animator `0x7f02004e / 0056` | 背景入场 alpha 0→1 / 750ms；展开时 scale 1，收起时 1.1 |
| animator `0x7f020048 / 0051`、integer `0x7f0c004b` | 背景展开/收起缩放 1↔1.1 / 400ms |
| interpolator `0x7f010076` | cubic-bezier(0.2, 0.1, 0, 1) |
| layout `0x7f0e0136` + 运行时圆点节点 | 圆点 8dp，步长 16dp，右边距 56dp |
| `myh.x()`、`myh.Q()` | 前景使用平移、透明度及整组缩放协调，不用逐帧改标题字体进行上下转换 |
| `myp.a()`、`myw.i()/j()` | 选中位置更新圆点、重置轮播时钟；自动轮播受焦点、页面状态、无障碍条件门控 |
| `GridLayoutManager` → `akz.al` → `fxf` → `ob(4)` → `a.ar` | 水平焦点滚动使用五次缓出 `1 + (t-1)^5`，时长取注入配置 |
| `aept` 默认配置 → `ffb.f()` | `horizontal_smooth_scroll_by_duration_base` = 500ms，水平滚动 dy=0 时直接使用该值 |
| `aept` 默认配置 | `autoscroll_feature_card_duration_ms` = 12000ms |

`wye.d()` 的完整几何计算存在反编译不完整区域。
`mwf` 分支 2 的 30%/70% 淡出淡入用于两个独立预览之间，不能直接当成所有上下转换的曲线。
轮播自动翻页间隔和横向滚动速度允许服务配置覆盖；本地采用 APK 内的上述默认配置。

## 运行时尺寸

模拟器视口 3840 × 2160，640dpi，即 960 × 540dp。
`dumpsys activity ...HomeActivity gtv2` 给出视图树；
节点的布局坐标需结合运行时平移和缩放与截图核对。

| 状态 | 实际观察 |
| --- | --- |
| 轮播聚焦 | 左侧内容基线 58dp；首排图片上缘约 494dp；底部右侧圆点 |
| 首排聚焦 | 预览显示标题及来源；有数据时还显示简介、评分、年份和标签；首排图片上缘约 358dp；纯图片卡片 |
| 普通栏目聚焦 | 栏目标题约 64dp；图片上缘 120dp；标题 26sp，未选中栏目约 16sp；片名在选中图片下方 |
| 横向栏目 | 卡片间距 20dp；选中卡片对齐左侧阅读位置；每一栏保留自己的选择；选中行以上降低亮度 |

标题的 `setTextSize(1, ...)` 使用 dp，而非随系统字体缩放的 sp。
轮播的标题、附加信息与按钮按整页宽度横向移动，背景独立切换。

截图与原始视图树：`build/reports/google-tv-home-rebuild/official-*.png` 和
`official-*-dump.txt`。

## Animeko 对应范围

页面只包含番剧内容，不包含顶部 tab row 和系统应用栏。
navigation rail 使用主壳现有实现。继续观看使用在看收藏及沉浸预览；推荐使用自适应纵向网格。
热门仅在 Hero 轮播展示，覆盖共享分页的全部条目。番剧标题、图片和动作来自 Animeko。

首页组件使用上述尺寸、圆角、缩放及结构，系统 sans-serif 和中文回退字体承载文字。
Google 的品牌图、广告、影视目录、购买价格及后台服务不属于 Animeko 的内容模型。
背景遮罩、取色、中文排版和 Compose 滚动由本地实现完成；
轮播元信息与详情页共用组件和收藏状态，五项圆点视口通过边缘留白支持首尾项居中。

实现与测试入口见 [首页设计](home-design.md)。
