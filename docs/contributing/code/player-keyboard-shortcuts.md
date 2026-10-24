# 播放器键盘快捷键

播放器的键盘快捷键可以在「设置 - 播放器 - 键盘快捷键」里自定义。本文说明配置模型、按键匹配和设置页的职责划分。

## 配置模型 (`app-data`)

`PlayerShortcutAction` 枚举播放器里能用键盘触发的操作 (暂停 / 继续、全屏、弹幕、统计、前后 5 秒、音量、倍速)。

`PlayerShortcut` 是一个按键组合: 平台无关的按键标识 `key` 加上 Ctrl / Shift / Alt / Meta 四个修饰键。
标识与 Compose `Key` 常量同名 (如 `"Spacebar"`、`"DirectionLeft"`、`"NumPad1"`)。
Compose `Key` 的键码在 Android 和桌面不同，所以持久化的是标识而不是键码。

`PlayerShortcutConfig` 只持久化用户改过的操作 (`overrides`)，其余操作使用 `DEFAULT_BINDINGS`，
默认快捷键的调整因此会自动应用到没有自定义过该操作的用户。空列表表示操作没有快捷键。
`withShortcuts` 是唯一的写入口：它去重、把这些按键组合从其他操作里移除（一个组合只属于一个操作），
并把与默认值相同的操作从 `overrides` 中去掉。配置存放在 `SettingsRepository.playerShortcutConfig`，
随设置备份一起导入导出。

## 按键表与匹配 (`ui-foundation`)

`PlayerShortcutKeys` 维护标识、Compose `Key` 和展示名称的双向映射，也是可绑定按键的白名单。
Enter / Escape / Tab / DPad 中键留给页面导航和输入框，修饰键只能作为组合的一部分，这些都不在表里。
`KeyEvent.toPlayerShortcut()` 把按键事件转成组合，`PlayerShortcut.matches(...)` 做精确匹配，
`displayText(isMacOs)` 生成 `Ctrl + Shift + F` 或 macOS 风格的 `⌃⇧F`。

## 播放器分发 (`video-player`)

`rememberPlayerKeyboardShortcuts(shortcuts, ...)` 返回注册到 `PlayerControllerState.keyboard` 的 `PlayerKeyboardHandler`
（分发链路见 [播放器键盘与焦点](player-keyboard.md)），用 `PlayerShortcutResolver` 把每个按键事件解析成操作，再按操作类型分发:

- 开关类操作和后退在 KeyUp 触发，KeyDown 只消费事件。
- 音量在每次 KeyDown 触发，按住不放持续调节。绑定本身不含 Shift 时，按住 Shift 命中同一操作并作为微调；
  精确匹配优先，所以把 Shift+↑ 绑定给别的操作后它不再属于音量。
- 前进在 KeyDown 启动 200ms 计时进入倍速快进，提前松开则前进 5 秒。这段按住状态由 `FastSkipHold` 维护，
  与 `Modifier.keyboardSeekAndFastForward` 共用。
- KeyUp 沿用按下时解析到的操作，不看松开时的修饰键：Ctrl+→ 快进中先松 Ctrl 再松 → 也会结束快进。
  按住期间重复的 KeyDown 仍按当时的修饰键重新解析，结果变了就直接结束原操作，不触发它松开时的动作。
- 修饰键必须正好匹配：Ctrl+Space 不会触发只绑定了 Space 的操作。`PlayerKeyboardState.dispatch` 会把带 Ctrl / Alt / Meta 的按下交给处理器，
  没有绑定命中时才按「交给页面控件」处理，所以 Ctrl+F 这类自定义组合可以生效，而页面上的其他快捷键不受影响。
- 倍速操作在播放器不支持倍速时、音量操作在没有音量控制器时不消费事件，交还给页面。
- RTL 布局下，绑定在左右方向键上的前进 / 后退互换，让「后退」始终在视觉上的后方；其他绑定不受布局方向影响。

配置从 `EpisodeViewModel.playerShortcutConfig` 经 `EpisodeVideoImpl` 和 `LockableVideoGestureHost` 传入；
其他调用方使用 `PlayerShortcutConfig.Default`。Android TV 使用自身的遥控器处理，不经过这里。

## 设置页 (`ui-settings`)

播放器设置组里的「键盘快捷键」入口由 `rememberHasPhysicalKeyboard()` 门控：桌面端总是显示，
Android 通过 `InputManager` 检查已连接的字母键盘并随插拔更新，iOS 不显示。
入口打开 `DetailPaneRoutes.PlayerShortcuts` 子页面 (`PlayerShortcutsTab`)，按播放 / 进度 / 音量 / 倍速分组列出操作和当前键帽，
顶栏提供「恢复全部默认」。

点击一项打开编辑对话框：已绑定的组合以 InputChip 展示，点击移除；采集区获得焦点后监听按键并即时加入待定列表，
已属于其他操作的组合会给出冲突提示。只有「确认」才调用 `withShortcuts` 写入配置，「恢复默认」只重置待定列表。
采集区由显式的 `focusable()` 提供焦点目标，Android 触摸模式下点击也能获得焦点（`clickable` 自带的焦点目标只在键盘 / 方向键输入模式下接受焦点）。
编辑对话框和「恢复全部默认」确认框都注册应用的 `BackHandler`：桌面端没有节点消费的 Escape 经应用的返回分发器到达对话框，
只关闭对话框而不会退出设置页；采集区本身不拦截 Escape、Android 返回键和 Tab。
