# Web 数据源验证码处理架构

> 状态: 已实施 (2026-07)。四步全部落地: PageEvaluator/BlockReason、CookieJarFeature/WebSourceIdentity、
> WebSessionManager + 两端 CaptchaBrowser (旧 coordinator 体系已删)、iOS 降级 UX 与测试迁移。
>
> 本文档描述 `SelectorMediaSource` 查询链路中验证码 (Cloudflare / Turnstile / 图片验证码 /
> 滑块等) 与限流的处理架构。这是对旧版 `WebCaptchaCoordinator` 体系的整体重写,
> 旧实现的问题记录见文末[附录](#附录-旧实现的问题)。
>
> **v1 范围**: 验证码由用户在浏览器里**交互解决**。自动识别 (图片验证码 CNN、MacCMS 后台协议、
> 挑战自动通过) **不在 v1 实现**, 但架构预留了干净的接入点, 见[扩展点](#扩展点-自动解决与备用取数-v1-不实现)。

## 设计目标

1. **唯一真相来源**: "页面是否被挡" 与 "验证码是否解决" 必须用同一个判定函数, 且 *selector
   能解析出内容* 优先于一切启发式检测。
2. **无陈旧缓存**: 不存在任何 "记录在案的成功" 可以被盲目返回; "已解决" 只体现为可现场验证的
   cookie 和活浏览器会话。
3. **浏览器是逃生通道, 不是常驻模式**: 直连 HTTP 优先, 只在被挑战期间走浏览器, 站点恢复后自动降级回直连。
4. **平台层最薄化**: 平台代码只实现 "一个能被驱动的浏览器", 全部业务逻辑在 commonMain, 可用假浏览器在
   commonTest 全覆盖。
5. **限流不是验证码**: HTTP 429 / 站内冷却页走独立的重试路径, 不弹浏览器, 不误导用户。
6. **身份一致性**: HTTP 请求呈现的 cookie 与 User-Agent 必须和清掉挑战的浏览器完全一致
   (`cf_clearance` 绑定 UA)。

## 总体结构

```text
┌─ commonMain ────────────────────────────────────────────────────────┐
│                                                                     │
│  PageEvaluator            页面判决: Ok / EmptyContent / Blocked     │
│  WebSessionManager        解决编排 · 会话注册表 · 生命周期          │
│  WebSourceCookieJar       ktor CookiesStorage 实现 (构造时注入)     │
│  WebSourceIdentity        per-host User-Agent 对齐的 ktor 插件      │
│  InteractiveSolveDialog   交互解决对话框外壳 (Compose)              │
│  · · · · · · · · · · · · · · · · · · · · · · · · · · · · · · · · · · │
│  CaptchaSolver[]          [预留, v1 为空] 自动解决策略链           │
│  SearchRoute[]            [预留, v1 为空] 备用取数路由             │
│                                                                     │
├─ platform (每端只实现 CaptchaBrowser) ─────────────────────────────┤
│                                                                     │
│  desktop  → CefCaptchaBrowser        (JCEF)                         │
│  android  → WebViewCaptchaBrowser    (android.webkit.WebView)       │
│  ios      → 无实现, isSupported = false (未来可接 WKWebView)        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

数据流 (一次搜索):

```text
SelectorMediaSource.search
   └─ WebSessionManager.fetchPage(url, expectation)
        ├─ [直连] ktor GET (带 jar cookies + UA 覆写) ──→ PageEvaluator
        │     ├─ Ok / EmptyContent ──→ 返回 (不碰浏览器)
        │     └─ Blocked(Captcha) ──→ 记录 lastHttpBlockedAt
        │           └─ 有暖会话? ──→ 浏览器重载 ──→ PageEvaluator ──→ 仍 Blocked → 上抛
        ├─ [浏览器粘滞] 60s 内 HTTP 刚被挡过且有暖会话 ──→ 直接浏览器加载
        └─ Blocked 上抛后:
              ├─ Captcha  → solve(auto) [v1: solver 列表为空, 直接失败] → CaptchaRequired → UI chip
              │             chip 点击 → solve(interactive) → 用户在浏览器解决 → 成功则 restart
              └─ RateLimited → delay 后重试一次; 失败 → RateLimited 状态 → UI 倒计时
```

## 核心组件

### PageEvaluator — 唯一判决函数

所有 "这个页面算不算被挡" 的判断都经过它: 引擎解析路径、交互对话框自动关闭、浏览器会话页面加载
(以及未来的 auto-solve 成功判定), 共用同一个函数。这保证了 **solve 的成功标准与 retry 的成功标准恒等**,
"解决成功但重试仍失败" 在结构上不可能发生。

```kotlin
sealed interface PageExpectation {
    /** 期望解析出条目列表 (搜索结果页) */
    class SearchResults(val config: SelectorSearchConfig) : PageExpectation

    /** 期望解析出剧集列表 (条目详情页) */
    class SubjectDetails(val config: SelectorSearchConfig, val subjectUrl: String) : PageExpectation

    /** 无 selector 可用时的兜底 (视频页等) */
    data object AnyContent : PageExpectation
}

sealed interface PageVerdict<out T> {
    /** 解析成功。T = List<WebSearchSubjectInfo> / SelectedChannelEpisodes / Document */
    class Ok<T>(val value: T) : PageVerdict<T>

    /** 正常页面, 合法的 "无结果" */
    data object EmptyContent : PageVerdict<Nothing>

    class Blocked(val reason: BlockReason) : PageVerdict<Nothing>
}

sealed interface BlockReason {
    class Captcha(val kind: WebCaptchaKind) : BlockReason
    /** HTTP 429 或站内冷却页 */
    class RateLimited(val retryAfter: Duration?) : BlockReason
    data object NotFound : BlockReason
    /** 403 且无验证码特征 */
    class Forbidden(val status: Int) : BlockReason
}
```

**判决顺序 (硬规则, 有对应测试)**:

1. HTTP 404 → `NotFound`;
2. **按 expectation 解析; 解析出内容 → `Ok`, 直接结束** —— 即使启发式检测报警、即使状态码是
   4xx。selector 能解析就是最终真相;
3. 站内冷却页 (如 "请不要频繁操作") → `RateLimited`;
4. HTTP 429 → `RateLimited(Retry-After)`;
5. 启发式检测分类出验证码 → `Captcha(kind)`;
6. HTTP 403 / 468 无特征 → `Captcha(Unknown)` / `Forbidden`;
7. 以上都不是 → `EmptyContent`。

规则 2 的 "解析优先" 是整个设计的基石: 页面上出现 "captcha" 字样、嵌了 reCAPTCHA 脚本的正常页面,
只要 selector 能解析出条目/剧集, 就不会被误判为被挡。

### 启发式检测器 (WebCaptchaDetector)

降级为**纯分类器**: 只在解析失败后运行, 职责只是猜验证码类型, 用于决定 UI 文案与 auto-solve 策略。
判错的代价因此很低。规则相对旧版收紧:

- 删除 `"captcha" && ("<img" || "verify")` 这类宽泛兜底;
- 图片验证码必须有结构证据 (验证码输入框 + 提交按钮 + 验证码图片三件套);
- Cloudflare challenge / Turnstile / SafeLine 的特征规则保留。

### CaptchaBrowser — 平台适配器

平台只实现 "一个能被驱动的浏览器", 不含任何业务逻辑:

```kotlin
interface CaptchaBrowser : AutoCloseable {
    /** 真实 UA (CEF / WebView 各自的), 用于 HTTP 侧身份对齐 */
    val userAgent: String

    /** 主 frame 每次加载完成 (url, html) */
    val pageLoads: SharedFlow<LoadedPage>

    suspend fun navigate(url: String)
    suspend fun currentPage(): LoadedPage?

    /** 带 domain/path/expiry 等完整属性 (CEF); Android 降级为 name=value */
    suspend fun collectCookies(urls: List<String>): List<BrowserCookie>

    /** 视频资源嗅探 */
    fun setResourceInterceptor(handler: ((String) -> InterceptDecision)?)

    /** SwingPanel / AndroidView */
    @Composable
    fun View(modifier: Modifier)
}

interface CaptchaBrowserFactory {
    /** iOS = false */
    val isSupported: Boolean
    suspend fun create(): CaptchaBrowser
}
```

**线程铁律**:

- 适配器方法全部是 `suspend`, 内部自行 marshal 到 CEF/Main 线程;
- 浏览器回调线程 (CEF 的 EDT、Android 的 Main) 上**只允许** `tryEmit` / `complete`,
  **禁止任何形式的等待** (`runBlocking`、`invokeAndWait`、信号量等);
- cookie 收集用 `suspendCancellableCoroutine` 桥接回调, 由 manager 的协程消费, 永不阻塞 UI 线程。

### WebSessionManager — 编排核心

```kotlin
class WebSessionManager(
    private val browserFactory: CaptchaBrowserFactory,
    private val cookieJar: WebSourceCookieJar,
    private val evaluator: PageEvaluator,
    private val scope: CoroutineScope,
) {
    /** 引擎的唯一页面入口: 直连优先, 按需走浏览器 */
    suspend fun fetchPage(url: String, expectation: PageExpectation): PageVerdict<*>

    suspend fun solve(request: SolveRequest, interactive: Boolean): SolveOutcome

    suspend fun extractVideoResource(
        pageUrl: String,
        timeoutMillis: Long,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource?

    /** 只取消进行中的 auto-solve, 不清暖会话、不清 cookie */
    fun cancelAutoSolves()

    /** app 根部唯一 dialog host 消费此状态 */
    val interactiveUi: StateFlow<InteractiveSolveUi?>

    val isInteractiveSupported: Boolean get() = browserFactory.isSupported
}
```

#### 会话注册表

- key 为 **host** (去 `www.` 前缀)。cookie 本就是 host 级的, 同 host 的多个数据源共享一次解决成果。
  不再有 `mediaSourceId@host` 与 per-source fallback map;
- 每 host 最多一个活浏览器会话; LRU 上限 (桌面 3 / Android 2); **闲置 TTL (5 分钟) 自动回收**,
  回收时真正释放资源 (Android 端 `webView.destroy()`, 桌面端 dispose CEF browser + client + permit);
- 注册表全部状态由单个 `Mutex` 保护。

#### 直连优先与浏览器粘滞

per-host 记录 `lastHttpBlockedAt`:

- 默认走直连 HTTP (带 jar cookies + UA 覆写), 交给 `PageEvaluator`;
- 直连被挡且有暖会话 → 同一请求内用浏览器重载一次;
- 60 秒内 HTTP 刚被挡过且有暖会话 → 后续 `fetchPage` 直接走浏览器 (避免一次搜索的 1+N 个页面
  每个都先失败一次);
- 直连一旦恢复 `Ok` → 回到直连, 浏览器闲置直至 TTL 回收。

站点停止挑战后系统自愈, 不存在 "solved 一次, 该源终身走浏览器" 的路径。

#### solve 语义

- **没有 solvedResults 缓存**。"已解决" 只体现为 jar 里的 cookie 和注册表里的暖会话,
  两者都可现场验证、可失效;
- `solve(interactive = true)` **必定呈现对话框**。同 host 已有进行中的 solve 则 join
  (single-flight)。入口不查任何缓存 —— 用户点 "处理验证码" 本身就是 "当前状态不行" 的证明;
- `solve(interactive = false)` (auto): 遍历注入的 `solvers` 列表, 首个让 `evaluate` 返回 `Ok`
  的策略胜出。**v1 该列表为空 → 立即失败 → 上报 `CaptchaRequired`**。列表非空时的语义见
  [扩展点](#扩展点-自动解决与备用取数-v1-不实现); manager 侧无需为接入 solver 改动;
- **自动失效闭环**: `fetchPage` 发现 "刚 solve 成功却又 Blocked" 时, 自动丢弃该 host 的暖会话与
  jar 中相关 cookie, 下次 solve 从干净状态开始。失效不依赖任何人手动调用 reset;
- 防浏览器风暴: 全局并发上限 2; per-host 失败冷却 60s (v1 主要作用于交互解决与暖会话重验证)。

#### 交互解决对话框

- 对话框外壳 (黑底 + 顶栏: 返回 / 刷新 / ✓ 手动确认) 在 commonMain, 只有 `browser.View()` 来自平台;
- 自动关闭: manager 消费 `pageLoads` flow, 每次主 frame 加载完成后 evaluate;
  另保留 2 秒慢速快照兜底, 应付纯前端路由 (无导航事件) 的站点;
- 用户按 ✓ 手动确认时, 以当前页面快照 evaluate 的结果为准记录成败, 但都关闭对话框。

### WebSourceCookieJar 与 WebSourceIdentity

**cookie 通道** (`WebSourceCookieJar : CookiesStorage`):

- 通过 `HttpClientProvider` 的新 feature (`CookieJarFeature`) 在 HttpClient **构造时**注入,
  取代旧版对 ktor `HttpCookies` 私有字段的反射写入;
- 同时供 `SelectorMediaSource.matcher.patchConfig` 给播放器 WebView 注入 cookie ——
  HTTP、播放器、两个平台共用同一份 cookie 真相;
- cookie 存完整属性 (domain / path / expiry / secure)。域匹配规则: 精确 host **或** `.domain`
  后缀匹配, 保证 `cf_clearance` 这类域级 cookie 能覆盖到播放页所在子域;
- Android 拿不到属性时降级: 按页面 host (去 www) 存, 后缀匹配;
- **不做磁盘持久化** (有意取舍): CEF / WebView 自身的 cookie store 天然持久,
  重启后首次被挡时 auto-solve 会因浏览器仍持有 clearance 而快速通过。

**UA 通道** (`WebSourceIdentity` ktor 插件):

`cf_clearance` 绑定 User-Agent。旧版 HTTP 侧用 ktor 通用 `BrowserUserAgent()`, 与 CEF/WebView
真实 UA 不一致, 导致 cookie 同步正确也可能被再次挑战。新设计在 solve 成功时记录
`browser.userAgent`, 插件对该 host 的后续请求覆写 `User-Agent`, 保证 HTTP 侧身份与清掉挑战的浏览器一致。

## 扩展点: 自动解决与备用取数 (v1 不实现)

> v1 不实现任何自动解决。本节定义两个**预留接缝**, 使未来接入 (如 PR #3172 的图片验证码 CNN 识别)
> 成为纯增量: `WebSessionManager` 构造时接收两个列表, v1 均注入空列表, 接入时只需注册实现,
> **manager 与平台层不改动** (纯 HTTP 策略除外, 见下)。

### 接缝一: CaptchaSolver — 自动解决策略链

自动与交互的唯一区别是 "谁在驱动浏览器": 自动是一串策略依次尝试, 交互是最后兜底的 "人肉 solver"。
`solve(interactive = false)` 遍历 `solvers` 列表, 按 `canAttempt` 过滤 → 从便宜到贵依次 `attempt`
→ 首个让 `evaluate` 返回 `Ok` 的策略胜出, cookie 同步进 jar → 全部失败则上报 `CaptchaRequired`。
**所有策略共用同一个 `PageEvaluator` 判定成功**, 因此 "自动解成功" 与 "解完能继续搜索" 恒等。

```kotlin
interface CaptchaSolver {
    val id: String
    /** 便宜的预判 (按 kind / host 允许表) */
    fun canAttempt(reason: BlockReason.Captcha, host: String): Boolean
    /** 尝试解决; 成功与否由 ctx.evaluate 判定 */
    suspend fun attempt(ctx: SolveContext): SolveOutcome
}

class SolveContext(
    val request: SolveRequest,                         // url / expectation / kind / mediaSourceId
    val http: ScopedHttpClient,                        // jar 背书、UA 已对齐
    val acquireBrowser: suspend () -> CaptchaBrowser,  // 懒创建; 纯 HTTP 策略永不调用
    val evaluate: suspend (LoadedPage) -> PageVerdict<*>, // 唯一真相来源
)
```

接入时可能的策略 (以 PR #3172 为例, 按便宜→贵排序):

1. **纯 HTTP 图片验证码 (MacCMS)**: 不建浏览器 —— 请求验证码图 → recognizer → POST `verify_check`
   → `evaluate` 验证搜索页, 手动维护 `PHPSESSID` 会话。**不依赖 `CaptchaBrowser`, 因此 iOS 等无浏览器
   平台也能跑**;
2. **挑战自动通过 (Cloudflare/Turnstile)**: 无头浏览器等 JS 挑战。Image/Slider 不触发;
3. **浏览器 DOM 图片验证码**: 截图/填写/提交, 作为 HTTP 模式不适用时的后备。

接入所需的三处增量, 除此之外不动现有代码:

- **recognizer 注入点**: `ImageCaptchaRecognizer` (ONNX, 每端各自 runtime) 是注入 solver 的叶子,
  只做 "图片字节 → 四位数字", 不重试、不操作页面、不判成败 —— 这些留在 commonMain 的策略里保证平台一致;
- **DOM 驱动**: 浏览器 DOM 类策略需要给 `CaptchaBrowser` 增加一个
  `suspend fun executeJavaScript(script): String?`。纯 HTTP 策略不需要任何平台改动;
- **注册**: 把策略实例放进 `solvers` 列表, 通过 DI 注入 manager。

> **为何这样切分**: PR #3172 为绕开永不过期的 `solvedResults` 缓存, 专门给图片验证码加了
> `supportsBackgroundImageCaptchaRequests()` 分支去清缓存。本架构无 `solvedResults` 缓存, 每次 solve
> 都现场验证 (见 [solve 语义](#solve-语义)), 该特判在新架构里根本不需要 —— PR 独立撞上了旧实现
> [问题 2](#附录-旧实现的问题)。同理 PR 用 `hasSearchResultPage` 判定图片验证码是否解开, 正是
> `PageEvaluator` 的 "解析优先" 原则, 接入时统一走 `ctx.evaluate` 一个函数。

### 接缝二: SearchRoute — 备用取数路由

PR #3172 的 `GirigiriSearchBypass` **不是解验证码**, 而是在 HTML 搜索页被验证页挡住时改走站点公开
JSON API, 再把结果转成 selector 认得的 HTML 形状。这类 "换一条路取数" 建模为 `fetchPage` 在发起常规
请求**之前**查询的 `SearchRoute` 列表: 命中 host 的路由直接取数并交给 `PageEvaluator`, 绕过验证码而非
解决它; 未命中或路由失败则回落到常规请求 → 验证码流程。

路由是硬编码的 per-site 适配 (host 允许表 + 手写 HTML 拼接), 隔离在这一层, 不污染通用解析与 solver
抽象。v1 注入空列表。

## Fetch 状态与 UI

- `MediaSourceFetchState` 新增 `RateLimited(retryAt)`; 异常体系改为 `BlockedException(reason)`,
  `MediaFetcher` 按 reason 映射状态;
- 验证码 chip 点击链路: `resolveCaptcha` → `solve(interactive = true)` → Solved 则 restart 该源。
  因为 solve 入口无缓存, 点击必然弹框;
- 限流: chip 显示 "限流中 · Xs 后自动重试", 到点自动 restart 一次, 不弹浏览器;
- **iOS** (v1): 无 `CaptchaBrowser` 实现, `isInteractiveSupported = false`。UI 不显示 "处理验证码"
  按钮, 改为提示 "此源需要网页验证, 当前平台暂不支持, 请在桌面 / Android 端使用或更换数据源";
  fetch 快速失败并携带明确 reason。未来接 WKWebView (实现 `CaptchaBrowser`) 即获得交互能力;
  纯 HTTP 类 solver ([扩展点](#扩展点-自动解决与备用取数-v1-不实现)) 落地后甚至能在 iOS 无头自动解决,
  无需浏览器。

## 生命周期规则

| 事件 | 行为 |
|---|---|
| `EpisodeViewModel.onCleared` / 退出编辑源页 | `cancelAutoSolves()` — 只取消进行中的 auto-solve |
| 暖会话闲置超过 TTL / LRU 淘汰 | 释放浏览器资源 |
| solve 成功后同 host 再次 Blocked | 自动失效: 丢弃暖会话 + 相关 cookie |
| 导航 / 换集 | **不**影响暖会话与 cookie |

## 测试

核心逻辑全部在 commonMain, 用 `FakeCaptchaBrowser` (脚本化页面序列) 在 commonTest 覆盖。
关键用例 (均对应旧版真实故障):

1. 启发式误报 + selector 可解析 → `Ok` (解析优先);
2. cookie 过期后站点再次挑战 → interactive 必定弹框 (无陈旧缓存路径);
3. solve 成功后立刻又 Blocked → 自动失效 → 下次 solve 从干净状态开始;
4. HTTP 429 → `RateLimited`, 不创建浏览器;
5. 同 host 并发 solve → single-flight, 只弹一个对话框;
6. 闲置 TTL 回收 → `close()` 被调用 (防泄漏回归);
7. jar 域后缀匹配; UA 覆写只作用于已 solve 的 host;
8. **空 solver 列表** (v1): `solve(auto)` 立即失败并上报 `CaptchaRequired`, 不创建浏览器。

平台侧用 `desktop-ui-verify` / `android-ui-verify` skill 各做一次真机冒烟:
真实对话框弹出 → 解决 → restart 成功。

扩展点落地时补充: 假 recognizer + 脚本化 solver, 验证成功走 `PageEvaluator` (与交互同一判据)、
失败链式回落、全失败上报 `CaptchaRequired`; 命中 host 的 `SearchRoute` 绕过验证码直接取数。

## 实施顺序

每步独立可发布。v1 到第 4 步为止, 交付 "用户交互解决验证码" 的完整能力。

| 步骤 | 内容 | 消灭的问题 (见附录编号) |
|---|---|---|
| 1 | `PageEvaluator` + `BlockReason` + 检测器收紧, 引擎接入 (兼容旧 coordinator) | 1、4、5、12 |
| 2 | `CookieJarFeature` + `WebSourceIdentity` (UA 对齐), 删反射 | 11、UA 隐患 |
| 3 | `WebSessionManager` (含空 `solvers` / `searchRoutes` 接缝) + 两端 `CaptchaBrowser`, 删旧 coordinator, UI 状态接入 | 2、3、6、7、8、9、13 |
| 4 | iOS 降级 UX、死代码清理、测试迁移 | 10 |

**扩展点 (不属于 v1, 未来按需)**: 注册 `CaptchaSolver` / `SearchRoute` 实现即可接入自动解决,
如吸收 PR #3172 (图片验证码 ONNX 识别 + MacCMS HTTP 协议 + Girigiri 备用路由)。详见
[扩展点](#扩展点-自动解决与备用取数-v1-不实现) —— manager 与平台层无需改动 (DOM 类 solver 需给
`CaptchaBrowser` 加 `executeJavaScript`)。

随步骤 3/4 删除的旧代码: `WebCaptchaCoordinator` 及两端实现、`WebCaptchaSearchProbe`、
`selectSolvedSessionKey` / `solvedByMediaSource`、`NoopWebCaptchaCoordinator`、
`WebCaptchaCoordinatorHolder`、三端 `WebCaptchaCookieStorage.*` (反射实现)。

## 附录: 旧实现的问题

重写前 (2026-07) 对旧 `WebCaptchaCoordinator` 体系的审查结论, 编号与上文实施顺序表对应。

严重问题:

1. **判定标准不一致导致死循环**: solve 成功由 selector probe 判定, 但解决后重试路径
   (`parseSearchResult`) 只看启发式检测器。被误报的正常页面 → solve "成功" 但重试永远失败,
   无限循环;
2. **陈旧缓存不可恢复**: `solvedResults` 永不过期, 且 `tryAutoSolve` / `solveInteractively`
   入口命中缓存直接返回旧 `Solved`。cookie 过期后站点再次挑战时, 用户点 "处理验证码"
   瞬间返回旧缓存, 对话框不弹; `resetSolvedSession` 在整个 App 无调用方, 只能重启 App;
3. **EDT 阻塞 + 空 cookie**: 桌面端页面观察者回调 (CEF 在 EDT 上触发) 内 `runBlocking`
   收集 cookie, 而收集又需向 EDT 投任务 → 每 URL 1s 超时 ×4 ≈ 4s UI 冻结, 且收到的 cookie 为空;
4. **限流被包装成验证码**: 403/429 一律按验证码处理, 真限流时弹浏览器也不可能通过,
   用户被误导去解一个不存在的验证码;
5. **检测器误报面过大**: `"captcha" && ("<img" || "verify")` 兜底规则几乎命中所有提到 captcha
   的正常页面; 条目页无 probe 兜底, 误报导致条目被静默跳过。

架构 / 生命周期问题:

6. **桌面 auto-solve 成功即销毁会话**: `rememberSolved` 放回 map 后 `finally { disposeSession }`
   又移除并 cancel, 之后每次 "solved 会话页面加载" 都新建整个 CEF browser
   (一次搜索 = 1+N 次浏览器创建销毁); 且 solved 后永远走浏览器加载, 无退出机制;
7. **`cancelAutoResolutionRequests` 名不副实且两端不一致**: 桌面端把交互解决后特意保留的会话连同
   solvedResults 一起清掉 (换集即失效); Android 端根本没有 override (空实现);
8. **Android 端线程安全与泄漏**: 三个注册表 map 无锁并发读写; WebView 从不 `destroy()`;
9. **平台行为不一致**: Android `getSolvedCookies` 只查精确 key, 桌面走 fallback 查找,
   注入播放器的 cookie 两端可能不同;
10. **iOS 空架子**: Noop coordinator + no-op cookie 存储, 但 UI 共享 —— 用户看到验证码 chip,
    点击无任何反应、无提示。

健壮性问题:

11. **反射写 ktor 私有字段**: `storeCaptchaCookies` 反射拿 `HttpCookies.storage`, ktor 升级即碎、
    失败静默; cookie 丢失 domain/expiry 属性, 域级 cookie 覆盖不到子域;
12. `detectCaptchaKindFromBlockedResponse` 存在 `?: if (...) Unknown else Unknown` 死分支;
13. **交互解决单槽位**: `interactiveSolveState` 只有一个, 并发两个源需要交互验证时后者顶掉前者的对话框。

另有一个独立隐患: HTTP 侧 UA (ktor `BrowserUserAgent()`) 与浏览器真实 UA 不一致,
`cf_clearance` 绑定 UA, 即使 cookie 同步正确也可能被再次挑战 (见 [WebSourceIdentity](#websourcecookiejar-与-websourceidentity))。
