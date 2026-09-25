# Web 数据源验证码处理架构

本文档说明 `SelectorMediaSource` 查询链路如何处理验证码 (Cloudflare / Turnstile / 图片验证码 / 滑块等)
与限流: 设计目标、页面请求的流向, 以及各组件必须遵守的约束。类与函数的细节见各源文件的 KDoc。

图片验证码的识别模型与 MacCMS 后台协议见[图片验证码自动识别](../image-captcha.md)。

## 设计目标

1. **唯一真相来源**: "页面是否被挡" 与 "验证码是否解决" 用同一个判定函数, 且 *selector
   能解析出内容* 优先于一切启发式检测。
2. **无陈旧缓存**: 不存在任何 "记录在案的成功" 可以被盲目返回; "已解决" 只体现为可现场验证的
   cookie 和活浏览器会话。
3. **浏览器是逃生通道, 不是常驻模式**: 直连 HTTP 优先, 只在被挑战期间走浏览器, 站点恢复后自动降级回直连。
4. **平台层最薄化**: 平台代码只实现 "一个能被驱动的浏览器", 全部业务逻辑在 commonMain, 可用假浏览器在
   commonTest 全覆盖。
5. **限流不是验证码**: HTTP 429 / 站内冷却页走独立的重试路径, 不弹浏览器, 不误导用户。
6. **身份一致性**: HTTP 请求呈现的 cookie 与 User-Agent 必须和清掉挑战的浏览器完全一致
   (`cf_clearance` 绑定 UA)。

## 组件与数据流

| 组件 | 职责 |
|---|---|
| [PageEvaluator] | 唯一判决函数: `Ok` / `EmptyContent` / `Blocked(reason)` |
| [WebSessionManager] | 页面入口 `fetchPage`、解决编排 `solve`、浏览器会话注册表 |
| [CaptchaBrowser] | 平台适配器: 桌面 JCEF、Android WebView; iOS 无实现 |
| [CaptchaSolver] | 自动解决策略, 以列表注入 manager |
| [SearchRoute] | 备用取数路由, 以列表注入 manager |
| [WebSourceCookieJar] / [WebSourceIdentity] | HTTP 侧与浏览器共享的 cookie 和 User-Agent |

各组件在 [CommonKoinModule] 中组装, solver 与路由的列表顺序即尝试顺序。

```text
SelectorMediaSource
   └─ WebSessionManager.fetchPage(url, expectation)
        ├─ [已验证页] 自动 solver 刚保留了该 URL 的业务页 ──→ 一次性消费, 不发请求
        ├─ [备用路由] host 命中 SearchRoute ──→ 路由取数; 返回 null 则继续往下
        ├─ [浏览器粘滞] 60s 内 HTTP 刚被挡过且有暖会话 ──→ 直接浏览器加载
        └─ [直连] ktor GET (带 jar cookies + UA 覆写) ──→ PageEvaluator
              ├─ Ok / EmptyContent ──→ 返回 (不碰浏览器)
              └─ Blocked(Captcha) ──→ 有暖会话则浏览器重载一次; 仍 Blocked → 自动失效并返回

   Blocked 返回给 SelectorMediaSource 后:
        ├─ RateLimited → delay 后重试一次; 仍失败 → UI 显示限流倒计时, 到点自动重试
        ├─ Captcha     → solve(interactive = false)
        │                 ├─ Solved → 重新 fetchPage
        │                 └─ 失败   → UI 显示验证码 chip
        │                              点击 → solve(interactive = true) → 用户在浏览器解决 → 重启该源
        └─ NotFound    → 视为无结果
```

## PageEvaluator: 唯一判决函数

所有 "这个页面算不算被挡" 的判断都经过 [PageEvaluator]: 引擎解析、交互对话框自动关闭、浏览器会话页面加载、
自动 solver 的成功判定与备用路由的结果判定, 共用同一个函数。这保证了 **solve 的成功标准与 retry
的成功标准恒等**, "解决成功但重试仍失败" 在结构上不可能发生。

**判决顺序**是硬规则: 完整的有序列表写在 [PageEvaluator] 的 KDoc 中, 由 `PageEvaluatorTest`
逐条覆盖。顺序本身承载了三个设计决定:

- **解析优先于一切启发式检测**。404 之外, 第一步就是按 expectation 解析; 解析出内容即为 `Ok`
  并直接结束 —— 即使启发式检测报警、即使状态码是 4xx。这是整个设计的基石: 页面上出现 "captcha"
  字样、嵌了 reCAPTCHA 脚本的正常页面, 只要 selector 能解析出条目/剧集, 就不会被误判为被挡。
- **限流先于验证码分类**。站内冷却页与 HTTP 429 在启发式检测之前判定为 `RateLimited`,
  保证限流永远不会被当成验证码而弹出浏览器。
- **结构化 selector 页面上的无特征 403 按未知验证码处理**, 以保留浏览器逃生通道。部分 WAF
  (如次元城使用的防护) 会直接返回不带稳定特征的 403; 若将其归类为 `Forbidden`, 交互验证和自动 solver
  都不会运行。没有 selector 期望的页面 (`AnyContent`) 无从验证, 因此仍归为 `Forbidden`,
  避免把普通权限错误误报为验证码。

启发式检测器 [WebCaptchaDetector] 因此只是**纯分类器**: 只在解析失败后运行, 只负责猜验证码类型以决定 UI
文案与可用的自动 solver, 判错的代价很低。它的规则刻意保持严格 —— 图片验证码必须有结构证据
(输入框 + 提交按钮 + 验证码图片), 没有 "页面提到 captcha" 这类宽泛兜底。

## WebSessionManager: 会话与解决编排

**会话注册表**以 host (去 `www.` 前缀) 为 key: cookie 本就是 host 级的, 同 host 的多个数据源共享一次解决成果。
每 host 最多一个活浏览器, 有 LRU 上限与闲置 TTL, 回收时真正释放平台资源。

**直连优先与浏览器粘滞**: 默认走直连 HTTP; 直连被挡且有暖会话时, 同一请求内用浏览器重载一次,
并在随后的粘滞窗口内直接走浏览器 (避免一次搜索的 1+N 个页面每个都先失败一次); 直连一旦恢复 `Ok`
就回到直连, 浏览器闲置直至回收。站点停止挑战后系统自愈, 不存在 "solved 一次, 该源终身走浏览器" 的路径。

**solve 语义**:

- 没有 solved 结果缓存。"已解决" 只体现为 jar 里的 cookie 和注册表里的暖会话, 两者都可现场验证、可失效;
- `solve(interactive = true)` **必定呈现对话框**, 入口不查任何缓存 —— 用户点 "处理验证码"
  本身就是 "当前状态不行" 的证明。同 host 已有进行中的 solve 则 join (single-flight);
  多个 host 同时需要交互时排队依次呈现;
- 对话框在每次主 frame 加载完成后用 `PageEvaluator` 判定是否自动关闭, 另有慢速快照轮询兜底,
  应付无导航事件的纯前端路由站点;
- **自动失效闭环**: `fetchPage` 发现 "刚 solve 成功却又 Blocked" 时, 自动丢弃该 host 的暖会话与相关
  cookie, 下次 solve 从干净状态开始。失效不依赖任何人手动 reset;
- 防浏览器风暴: 同时创建浏览器有全局并发上限, 自动 solve 失败后有 per-host 冷却。

**生命周期**: 离开播放页或编辑源页只调用 `cancelAutoSolves()` 取消进行中的自动 solve;
导航与换集**不**影响暖会话与 cookie, 它们只由闲置 TTL、LRU 淘汰和自动失效回收。

## CaptchaBrowser: 线程约束

平台只实现 "一个能被驱动的浏览器" ([CaptchaBrowser]), 不含任何业务逻辑。实现必须遵守:

- 适配器方法全部是 `suspend`, 内部自行切换到 CEF/Main 线程;
- 浏览器回调线程 (CEF 的 EDT、Android 的 Main) 上**只允许** `tryEmit` / `complete`,
  **禁止任何形式的等待** (`runBlocking`、`invokeAndWait`、信号量等);
- cookie 收集用 `suspendCancellableCoroutine` 桥接回调, 由 manager 的协程消费, 永不阻塞 UI 线程。

iOS 没有浏览器实现: 不支持交互解决, UI 以提示代替 "处理验证码" 按钮; 不依赖浏览器的自动 solver 照常工作。

## Cookie 与 User-Agent

[WebSourceCookieJar] 在 HttpClient 构造时注入, 同时供播放器 WebView 注入 cookie —— HTTP、播放器、
各平台共用同一份 cookie 真相。

- 域匹配为精确 host **或** `.domain` 后缀匹配, 保证 `cf_clearance` 这类域级 cookie 能覆盖到播放页所在子域。
  Android 拿不到 cookie 属性, 降级为按页面 host 存储并后缀匹配;
- **不做磁盘持久化** (有意取舍): CEF / WebView 自身的 cookie store 天然持久, 重启后首次被挡时浏览器仍持有
  clearance, 交互解决会很快通过。

`cf_clearance` 绑定 User-Agent: HTTP 侧的 UA 与浏览器真实 UA 不一致时, 即使 cookie 同步正确也会被再次挑战。
因此 solve 成功时记录浏览器的 UA, [WebSourceIdentity] 插件对该 host 的后续请求覆写 `User-Agent`;
未 solve 过的 host 不受影响。

## 自动解决与备用取数

**[CaptchaSolver]**: 自动与交互的唯一区别是 "谁在驱动浏览器"。`solve(interactive = false)` 在用户开启了自动解决、
且该 host 不在失败冷却期时, 按成本从低到高依次尝试适用的策略, 首个成功者胜出; 没有适用策略时立即失败,
不创建浏览器。所有策略都用 `PageEvaluator` 判定成功, 因此 "自动解成功" 与 "解完能继续搜索" 恒等。
浏览器是懒创建的, 纯 HTTP 策略不会创建浏览器, 因而在 iOS 上也可用。

当前注册的是两个图片验证码策略 (纯 HTTP 的 MacCMS 协议, 以及浏览器 DOM 后备), 见
[图片验证码自动识别](../image-captcha.md)。识别器只做 "图片 → 数字", 重试、操作页面与判定成败都留在
commonMain 的策略中, 以保证平台一致。

**已验证页的一次性交接**: 纯 HTTP 验证码可能只放行当前这一次搜索请求, 验证成功后再由数据源重新请求同一 URL
会立刻再次触发验证码。因此 solver 可以交出已经由 `PageEvaluator` 验证过的业务页, manager 短暂保留它,
仅供**精确同 URL** 的下一次 `fetchPage` 消费**一次**。这不是 solved 缓存: 它保存的是已验证的页面内容,
而不是 "已解决" 的结论。

**[SearchRoute]** 不是解验证码, 而是 "换一条路取数": `fetchPage` 在发起常规请求之前查询命中 host 的路由,
路由取数后同样交给 `PageEvaluator` 判定; 不适用或失败则回落到常规请求与验证码流程。路由是硬编码的 per-site
适配, 隔离在这一层, 不污染通用解析与 solver 抽象。当前注册的是 [GirigiriSearchRoute]。

增加策略或路由: 实现对应接口并在 [CommonKoinModule] 的列表中注册, manager 与平台层无需改动。

## 测试

核心逻辑全部在 commonMain, 用 `FakeCaptchaBrowser` (脚本化页面序列) 在 commonTest 覆盖。
以下关键用例各对应一个不可回归的约束 (编号被 `WebSessionManagerTest` 的注释引用, 增删时保持稳定):

1. 启发式误报 + selector 可解析 → `Ok` (解析优先);
2. cookie 过期后站点再次挑战 → interactive 必定弹框 (无陈旧缓存路径);
3. solve 成功后立刻又 Blocked → 自动失效 → 下次 solve 从干净状态开始;
4. HTTP 429 → `RateLimited`, 不创建浏览器;
5. 同 host 并发 solve → single-flight, 只弹一个对话框;
6. 闲置 TTL 回收 → `close()` 被调用 (防泄漏回归);
7. jar 域后缀匹配; UA 覆写只作用于已 solve 的 host;
8. 无可用 solver → `solve(auto)` 立即失败, 不创建浏览器;
9. 自动 solver 交出的已验证页只交给精确同 URL 的下一次请求, 只消费一次, 且会过期。

JCEF / WebView 的真实行为用 `desktop-ui-verify` / `android-ui-verify` skill 做冒烟验证。

[PageEvaluator]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/PageEvaluator.kt
[WebCaptchaDetector]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/WebCaptchaSupport.kt
[WebSessionManager]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/WebSessionManager.kt
[CaptchaBrowser]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/CaptchaBrowser.kt
[CaptchaSolver]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/CaptchaSolver.kt
[SearchRoute]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/CaptchaSolver.kt
[GirigiriSearchRoute]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/GirigiriSearchRoute.kt
[WebSourceCookieJar]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web/captcha/WebSourceCookieJar.kt
[WebSourceIdentity]: ../../../../app/shared/app-data/src/commonMain/kotlin/domain/foundation/WebSourceHttpFeatures.kt
[CommonKoinModule]: ../../../../app/shared/application/src/commonMain/kotlin/platform/CommonKoinModule.kt
