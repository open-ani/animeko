Automatically created from tag $GIT_TAG. Do not change anything until assets are
uploaded.


[//]: # (ANI-SERVER-MAGIC-SEPARATOR)

[//]: # (注意: api server 依赖这个特殊分隔符)

[//]: # (对于所有可用的变量列表, 参考 CI release.yml 的 step release-notes)

[github-win-x64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-windows-x86_64.zip

[github-mac-x64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-macos-x86_64.dmg

[github-mac-aarch64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-macos-aarch64.dmg

[github-android]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-universal.apk

[github-android-arm64-v8a]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-arm64-v8a.apk

[github-android-armeabi-v7a]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-armeabi-v7a.apk

[github-android-x86_64]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-x86_64.apk

[cf-win-x64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-windows-x86_64.zip

[cf-linux-x64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-linux-x86_64.appimage

[cf-mac-x64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-macos-x86_64.zip

[cf-mac-aarch64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-macos-aarch64.dmg

[cf-ios]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION.ipa

[cf-android]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-universal.apk

[cf-android-arm64-v8a]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-arm64-v8a.apk

[cf-android-armeabi-v7a]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-armeabi-v7a.apk

[cf-android-x86_64]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-x86_64.apk

[ghproxy-win-x64]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-windows-x86_64.zip

[ghproxy-mac-x64]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-macos-x86_64.zip

[ghproxy-linux-x64]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-linux-x86_64.appimage

[ghproxy-mac-aarch64]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-macos-aarch64.dmg

[ghproxy-ios]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION.ios

[ghproxy-android]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-universal.apk

[ghproxy-android-arm64-v8a]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-arm64-v8a.apk

[ghproxy-android-armeabi-v7a]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-armeabi-v7a.apk

[ghproxy-android-x86_64]: https://ghfast.top/?q=https%3A%2F%2Fgithub.com%2Fopen-ani%2Fani%2Freleases%2Fdownload%2F$GIT_TAG%2Fani-$TAG_VERSION-x86_64.apk

[macOS 无法打开解决方案]: https://myani.org/wiki/macos-unable-to-open

[Windows下字体与背景颜色异常解决方案]: https://myani.org/wiki/windows-font-bg-color-issue

[Linux 安装说明]: https://myani.org/wiki/linux-install

[macOS Intel芯片版本安装教程]: https://myani.org/wiki/macos-intel-install

[macos-intel-issue]: https://github.com/open-ani/animeko/issues/1345

[linux-issue]: https://github.com/open-ani/animeko/issues/944

[iOS 自签]: https://myani.org/wiki/ios-install

|                  | 下载                                               | 常见问题                                        |
|------------------|--------------------------------------------------|---------------------------------------------|
| 安卓 手机/平板         | [主线][cf-android] / [备线][ghproxy-android]         |                                             |
| 安卓 电视            | 仍在开发中                                            | 现在可以使用平板版接鼠标使用                              |
| iOS              | [主线][cf-ios] / [备线][ghproxy-ios]                 | 需要[自签][iOS 自签]                                  |
| Windows          | [主线][cf-win-x64] / [备线][ghproxy-win-x64]         | 1. 目录不要有中文或者空格 <br/> 2. [显示异常][Windows下字体与背景颜色异常解决方案] |
| macOS (M 系列芯片)   | [主线][cf-mac-aarch64] / [备线][ghproxy-mac-aarch64] | ⚠️ [安装后打不开][macOS 无法打开解决方案]                      |
| macOS (Intel 芯片) | [主线][cf-mac-x64] / [备线][ghproxy-mac-x64]         | ⚠️ [安装教程][macOS Intel芯片版本安装教程]                    |
| Linux AppImage   | [主线][cf-linux-x64] / [备线][ghproxy-linux-x64]     | ⚠️ [安装教程][Linux 安装说明]                        |

[github-android-qr]: https://github.com/open-ani/ani/releases/download/$GIT_TAG/ani-$TAG_VERSION-universal.apk.github.qrcode.png

[cf-android-qr]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION-universal.apk.cloudflare.qrcode.png

[cf-ios-qr]: https://d.myani.org/$GIT_TAG/ani-$TAG_VERSION.ipa.cloudflare.qrcode.png

| 安卓 手机/平板                     | iOS IPA (需要[自签][iOS 自签])     | 
|------------------------------|--------------------------|
| ![Cloudflare][cf-android-qr] | ![Cloudflare][cf-ios-qr] |


<details>
<summary> Android 细分架构下载 </summary>

[//]: # (@formatter:off  因为"版本"前面不能换行)

如果不知道自己是什么架构，建议下载 `universal` 版本。

[//]: # (@formatter:on)

| 处理器架构              | 适用于             | 下载                                                                                                      |
|--------------------|-----------------|---------------------------------------------------------------------------------------------------------|
| universal (推荐)     | 所有设备            | [主线][cf-android] / [备线][ghproxy-android] / [GitHub][github-android]                                     |
| arm64-v8a (64 位)   | 几乎所有手机和平板       | [主线][cf-android-arm64-v8a] / [备线][ghproxy-android-arm64-v8a] / [GitHub][github-android-arm64-v8a]       |
| armeabi-v7a (32 位) | 旧手机             | [主线][cf-android-armeabi-v7a] / [备线][ghproxy-android-armeabi-v7a] / [GitHub][github-android-armeabi-v7a] |
| x86_64             | Chromebook 及模拟器 | [主线][cf-android-x86_64] / [备线][ghproxy-android-x86_64] / [GitHub][github-android-x86_64]                |

</details>
