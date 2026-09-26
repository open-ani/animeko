# 参与开发

欢迎你提交 PR 参与开发。

## 获取帮助

开发文档一直都是一个进行中的工作。如你对项目结构有任何疑问，欢迎通过以下途径寻求帮助：

- Telegram
  开发者群 [![Group](https://img.shields.io/badge/Telegram-2CA5E0?style=flat-squeare&logo=telegram&logoColor=white)](https://t.me/openani_dev) (
  推荐，开发者即时回复)

## 上手指南

建议按顺序阅读：

1. [开发工具和环境](setup.md): IDE, JDK, Android SDK
2. [运行和调试 APP](running.md): 运行 PC / Android / iOS 调试版本
3. [代码风格与代码规范](code-style.md)
4. [项目架构](architecture.md)
5. [构建和打包](building.md): 如何编译, 如何打包 APK
6. [编写和运行测试](testing.md)
7. [常见开发任务](common-tasks.md): 预览 Compose UI, 找到想修改的页面, 增加新页面模块

## 开发文档

- [Kotlin 多平台](kmp.md)
- [条目系统](code/subjects.md)
- [Android TV 导航焦点](code/android-tv-focus.md)
- [Android TV 加载占位](code/android-tv-loading.md)
- [Media Framework](code/media-framework.md)
    - [MediaSource](code/media/media-source.md)
    - [MediaSelector](code/media/media-selector.md)
    - [选源界面](code/media/media-selector-ui.md)
    - [缓存](code/media/media-cache.md)
    - [下载管理](code/media/media-downloads.md)
    - [Web 数据源验证码处理](code/media/web-captcha.md)
    - [代码地图](code/media/media-code-map.md)
- [图片验证码自动识别](code/image-captcha.md)
    - [采集训练样本](code/image-captcha-sampling.md)

> 以上文档除有标注外为人工编写。
> 其他部分文档可以参考 DeepWiki (AI)（有很高正确性）：<https://deepwiki.com/open-ani/animeko>

## 更多信息

- [查找待解决的问题](issues.md) (如何查阅 issues)
    - [Issue 属性列表](issue-labels.md)
- [PR 审核](code-style.md#pr-review-惯例)
