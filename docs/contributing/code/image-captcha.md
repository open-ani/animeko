# 图片验证码自动识别

图片验证码链路位于 `app/shared/app-data`，由公共编排、平台浏览器或 HTTP 会话和平台识别器三部分组成。

- `WebSessionManager` 是页面加载与验证码处理的统一入口，`CaptchaSolver` 实现自动处理策略，
  `WebSourceCookieJar` 在普通请求与求解请求之间共享站点会话。
- `ImageCaptchaRecognizer` 是模型接入点。Android、Desktop 与 iOS 向公共图片验证码 solver
  注入各自实现，识别器只需接收原始图片并返回四位数字。
- 公共逻辑负责提取验证码图片、校验识别结果、填写并提交、验证页面状态，以及失败后的两次刷新重试。
- 自动识别分支在三次识别或验证仍未成功时直接进入现有 `CaptchaRequired` 状态，不会自动打开交互式 WebView/JCEF；仅当用户主动选择处理验证码时，才复用原有交互式填写流程。

模型不应负责重试、操作网页或判断验证码是否通过。这些行为必须保留在公共逻辑中，以保证平台行为一致。

## 平台实现

三个客户端平台都使用同一份 `captcha-v1.0.onnx` 和同一套输入、输出契约。模型只保存一份，位于
`app/shared/app-data/src/commonMain/composeResources/files/captcha-v1.0.onnx`，由 Compose Multiplatform
资源管理统一打包到 Android assets、Desktop classpath 与 iOS app bundle。识别器不再各自定位模型文件，而是通过
`app-data` commonMain 的公共入口读取这同一份资源。运行时依旧使用各平台的 ONNX Runtime：

- Android 使用 `onnxruntime-android`，通过 `readImageCaptchaModelBytes()` 读字节建 session；
- Desktop 使用 ONNX Runtime JVM，同样通过 `readImageCaptchaModelBytes()` 读字节建 session；
- iOS 使用 `onnxruntime-objc` CocoaPod。Compose 资源在 iOS 上是 app bundle 内的真实文件，而 ORTSession 只接受文件路径，因此 iOS 改用 `imageCaptchaModelUri()` 拿到资源的 `file://` URI，解析为路径后直接交给 ORTSession，无需读入字节或落临时文件。

Android 与 Desktop 同时支持从浏览器 DOM 提取图片；Android 与 iOS 还支持下述 MacCMS 后台请求协议。
iOS 当前没有交互式验证码 WebView，因此自动识别失败后会返回现有 `CaptchaRequired` 状态，但用户主动打开
交互式验证仍为不支持。这个限制与模型推理能力无关。

## 模型契约

各平台使用随应用资源交付的 `captcha-v1.0` 三模型集成 ONNX 模型。模型契约固定为：

- 输入 `input`：`float32 [B, 1, 32, 96]`，原图先转灰度，再以最近邻缩放，像素范围为 `[0, 1]`；
- 输出 `logits`：`float32 [B, 4, 10]`，四个位置分别取最大 logit 对应的数字；
- 模型源文件位于 `app/shared/app-data/src/commonMain/composeResources/files/captcha-v1.0.onnx`。

当前生产模型来自 `captcha-final-prod_20260717_124015`，文件为 1,173,896 bytes，SHA-256 为
`97731e093e77c69a768de81ed9d565bb5f81c6bef88df261b6dd460bca2cfd9a`。它由三个 93,904 参数的
`position_ds` 成员组成，集成参数总数为 281,712；训练数据快照 SHA-256 为
`def0786403436641ef5f56309dc466a289cdbece1e8dd16f552354b314345e96`。

对应评估运行在 481 张分来源平衡留出集上的四位完全准确率为 96.05%，字符准确率为 98.80%；分来源四位完全准确率为：
新优酷 96.27%、次元城动画 96.88%、饭团动漫 95.00%。生产模型使用完整冻结快照重新训练，本身不重复报告准确率。
该留出集覆盖多个来源，但不是完全盲测，不能直接作为真实部署准确率结论。运行时通过刷新图片并最多尝试三次提高成功机会；
后续替换模型时仍应保留独立、分来源的真实验证码评估报告。

模型初始化或推理失败时，识别器返回无结果，公共链路继续执行刷新重试并最终返回失败；自动识别分支不会向用户展示图片验证码页面。

### 后台请求协议

Android 与 iOS 对检测为图片验证码的 MacCMS 搜索页使用后台 HTTP 会话完成验证，不加载交互页面。该流程不按数据源逐个实现，而是复用站点共同使用的协议：

1. 直接请求 `/index.php/verify/index.html` 获取验证码图片；该请求会创建同一验证会话所需的 `PHPSESSID`，因此无需在求解前重复请求搜索页。
2. 将 CNN 返回的四位数字通过 `POST /index.php/ajax/verify_check?type=search&verify=...` 提交。
3. JSON 响应中的 `code` 为 `1` 后，后台求解器会在同一 Cookie 会话中请求搜索 URL，并用数据源现有 selector 验证返回的 HTML；selector 能解析出搜索结果，或搜索 URL 返回明确的空结果提示时，均视为有效搜索页。首页、冷却页和验证码页不会被空结果提示误判为成功。
4. 已验证的搜索页会一次性交给原数据源流程直接解析，避免验证码刚通过就因重复请求搜索 URL 再次触发验证。
5. 识别失败时重新请求图片；只有图片字节哈希发生变化后才执行下一次推理。

请求通过 `WebSourceCookieJar` 维护 Cookie，并在验证码图片请求发生同路径跨域重定向时采用新的 origin。这样可以处理数据源从旧域名迁移到新域名的情况，同时避免把跳转到外部图片 CDN 的 URL 当作站点 origin。

搜索验证码可能只对单次搜索或短期会话有效。`WebSessionManager` 不缓存通用的 `Solved` 结果；自动求解成功后只暂存已经由 `PageEvaluator` 验证过的业务页，并且仅允许紧接着的精确同 URL 请求在 60 秒内消费一次。若后续搜索再次检测到验证码，应重新取图、识别、提交并更新 Cookie。用户主动处理验证码时继续使用现有交互式填写流程。

## 采集训练样本

Debug 构建的数据源测试页面在检测到图片验证码后会显示“采集 100 个训练样本”。采集器使用与运行时识别
完全相同的浏览器会话和图片提取逻辑，图片默认写入应用媒体下载目录下的 `captcha-samples`：

- Desktop：应用数据目录下的 `media-downloads/captcha-samples`；
- Android：应用专属外部存储的 `Movies/captcha-samples`，外部存储不可用时回退到应用内部目录。

目录中包含原始图片和追加写入的 `manifest.jsonl`。清单记录文件名、数据源 ID、页面 URL、图片 URL、媒体
类型和采集时间。采集器不会猜测标签；训练前应完成人工标注或将已确认标签写入训练集，避免把错误预测作为
真值。

批量采集会在保存首张图片后主动刷新验证码，并等待下一张图片完成加载。若新图片的原始字节与上一张完全
相同，采集器会继续刷新，单张样本最多尝试 10 次；达到上限仍未变化时停止当前批次，避免把缓存图片重复写入
训练集。

如需在其他开发工具中采集，可直接调用：

```kotlin
collectImageCaptchaSamplesToDirectory(
    browser = browser,
    request = captchaRequest,
    count = 100,
    outputDirectory = outputDirectory,
)
```

单次调用最多采集 10,000 个样本，以避免误操作无限占用存储空间。
