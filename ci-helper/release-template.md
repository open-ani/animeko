Automatically created from tag $GIT_TAG. Do not change anything until assets are
uploaded.

----

### 下载

<details>
<summary>>> <h2>点我展开下载列表</h2> <<</summary>

[//]: # (注意, `checkLatestVersion` 有字符串处理, 修改标题和分隔符前务必查询)

[//]: # (对于所有可用的变量列表, 参考 CI release.yml 的 step release-notes)

[github-win-x64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-windows-x86_64.zip

[github-mac-x64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-macos-x86_64.dmg

[github-mac-aarch64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-macos-aarch64.dmg

[github-android]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-universal.apk

[github-android-arm64-v8a]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-arm64-v8a.apk

[github-android-armeabi-v7a]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-armeabi-v7a.apk

[github-android-x86_64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-x86_64.apk

[cf-win-x64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-windows-x86_64.zip

[cf-mac-x64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-macos-x86_64.dmg

[cf-mac-aarch64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-macos-aarch64.dmg

[cf-android]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-universal.apk

[cf-android-arm64-v8a]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-arm64-v8a.apk

[cf-android-armeabi-v7a]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-armeabi-v7a.apk

[cf-android-x86_64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-x86_64.apk

[ghproxy-win-x64]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-windows-x86_64.zip

[ghproxy-mac-x64]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-macos-x86_64.dmg

[ghproxy-mac-aarch64]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-macos-aarch64.dmg

[ghproxy-android]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-universal.apk

[ghproxy-android-arm64-v8a]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-arm64-v8a.apk

[ghproxy-android-armeabi-v7a]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-armeabi-v7a.apk

[ghproxy-android-x86_64]: https://mirror.ghproxy.com/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-x86_64.apk

### 适用于电脑

[macos解决方案]: https://github.com/open-ani/ani/wiki/macOS-%E6%97%A0%E6%B3%95%E6%89%93%E5%BC%80%E8%A7%A3%E5%86%B3%E6%96%B9%E6%A1%88

[windows解决方案]: https://github.com/open-ani/ani/wiki/Windows-%E4%B8%8B%E5%AD%97%E4%BD%93%E4%B8%8E%E8%83%8C%E6%99%AF%E9%A2%9C%E8%89%B2%E5%BC%82%E5%B8%B8

[macos-intel-issue]: https://github.com/open-ani/animeko/issues/1345

[linux-issue]: https://github.com/open-ani/animeko/issues/944

- macOS 安装后如果无法打开: [解决方案][macos解决方案]
- Windows 打开时如果显示异常: [解决方案][windows解决方案]
- **Windows 解压目录不要带中文, 尽量不要有空格**

| 操作系统和架构          | 下载                                                                              |
|------------------|---------------------------------------------------------------------------------|
| Windows          | [主线][cf-win-x64] / [备线][ghproxy-win-x64] / [GitHub][github-win-x64]             |
| macOS (M 系列芯片)   | [主线][cf-mac-aarch64] / [备线][ghproxy-mac-aarch64] / [GitHub][github-mac-aarch64] |
| macOS (Intel 芯片) | 不再支持. 如有需要, 请为此 issue 点赞: [#1345][macos-intel-issue]                            |
| Ubuntu           | 如有需要, 请为此 issue 点赞: [#944][linux-issue]                                         |

### 适用于 Android 手机和平板

| 处理器架构              | 适用于             | 下载                                                                                                      |
|--------------------|-----------------|---------------------------------------------------------------------------------------------------------|
| universal (推荐)     | 所有设备            | [主线][cf-android] / [备线][ghproxy-android] / [GitHub][github-android]                                     |
| arm64-v8a (64 位)   | 几乎所有手机和平板       | [主线][cf-android-arm64-v8a] / [备线][ghproxy-android-arm64-v8a] / [GitHub][github-android-arm64-v8a]       |
| armeabi-v7a (32 位) | 旧手机和部分电视        | [主线][cf-android-armeabi-v7a] / [备线][ghproxy-android-armeabi-v7a] / [GitHub][github-android-armeabi-v7a] |
| x86_64             | Chromebook 及模拟器 | [主线][cf-android-x86_64] / [备线][ghproxy-android-x86_64] / [GitHub][github-android-x86_64]                |

[//]: # (@formatter:off  因为"版本"前面不能换行)

> 如果不知道自己是什么架构, 建议下载 `universal` 版本: [主线][cf-android] / [备线][ghproxy-android] / [GitHub][github-android]

[//]: # (@formatter:on)

也可以扫描二维码下载适用于所有 Android 设备的安装包:

[github-android-qr]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-universal.apk.github.qrcode.png

[cf-android-qr]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-universal.apk.cloudflare.qrcode.png

| 全球                           | 中国大陆                         |
|------------------------------|------------------------------|
| ![GitHub][github-android-qr] | ![Cloudflare][cf-android-qr] |

### 适用于 iOS

[ios-issue]: https://github.com/open-ani/animeko/issues/496

iOS 版正在开发中. 可为此 issue 点赞以加快开发速度: [#496][ios-issue]

</details>
