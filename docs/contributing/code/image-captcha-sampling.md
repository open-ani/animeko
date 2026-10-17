# 采集图片验证码训练样本

本文说明如何为[图片验证码自动识别](image-captcha.md)的模型采集训练样本。

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
