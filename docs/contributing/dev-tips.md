# 开发提示

## UI

### 预览 Compose UI

因为项目目前支持 Android 和桌面 JVM 两个平台，预览也就分两个平台。
绝大部分 UI 代码在 Android 的桌面的效果是一模一样的，因此使用一个平台的预览即可。

在 common 中使用 `@Preview` 将进行桌面平台的预览，但桌面预览不支持可交互式预览 (Interactive Mode),
也不支持即时刷新，不推荐。

在开发时，通常建议在 `androidMain` 中编写预览代码。你可以通过 IDE 分屏功能将 Android 预览放到一边。

<img width="600" src="images/previewing-compose-ui.png" alt="previewing-compose-ui"/>

#### 查找已有页面的预览

用 IDE 查找想要预览的 Composable 的实现就能找到 (按住 Ctrl 点击函数名)。许多页面都有 Android
的预览。

<img width="400" src="images/find-existing-previews.png" alt="previewing-compose-ui"/>

### 如何找到我想修改的页面/功能?

从 UI 入手，全局搜索页面上会显示的文字的字符串最简单。

你也可以用遍历的方式：

1. 所有 UI 页面都在 `app/shared/ui-*` 目录下。
2. 进入每个目录看它们的 `XXXPage` 或 `XXXScreen` 文件。

### 如何增加一个新页面模块

1. 在 `app/shared/` 目录下创建一个对应的目录 `ui-xxx`，可以从已有的页面模块中复制一个；
2. 在 `settings.gradle.kts` 中添加 `includeProject(":app:shared:ui-xxx")`；
3. 在 IDE 刷新 Gradle，导入项目；
4. 在 `app/shared/build.gradle.kts` 中仿照现有代码，
   添加一行 `api(projects.app.shared.uiXxx)`；
5. 删除复制过来的代码，开始编写新页面。
