# 播放器键盘与焦点

`PlayerKeyboardScope(controllerState, modifier, content)` 定义使用播放器作为默认键盘目标的子树。
`VideoScaffold` 包含独立播放器的 scope，`EpisodeScreen` 的 scope 覆盖整个播放页面。
预览按键阶段将快捷键交给 `PlayerControllerState` 持有的处理器，因此标签页、按钮、列表和进度条持有焦点时，
Space、方向键和其他播放快捷键仍作用于视频。嵌套 scope 的外层先消费事件，每个事件只处理一次。

`PlayerGestureHost` 组装并通过 `DisposableEffect` 注册快捷键处理器，使用当前 seek、快进、音量、倍速和切换回调。
最后一次注册生效；只有当前处理器的注销才能清除注册。播放器节点负责 Enter / NumPadEnter 拦截、焦点和手势。
Enter 激活页面内其他控件，Tab 保持焦点遍历。
快捷键只接受未按住 Ctrl、Alt 或 Meta 的 KeyDown；Shift 可用于音量微调。
被拒绝的按键直到 KeyUp 都交给页面控件，提前松开修饰键也不会触发播放器快捷键。

scope 使用 `InterceptPlatformTextInput` 统计正在运行的输入法会话，进入会话前增加计数，结束时在 `finally` 中减少。
计数由同一 controller 的 scope 共享，内部输入框的会话也能阻止外层 scope 分发快捷键。
有输入会话或 `PlayerFocusTarget.TEXT_INPUT` 偏好时，文本、空格和方向键交给输入框。
输入开始会取消正在等待或执行的键盘快进；只有已处理的 KeyDown 对应的 KeyUp 才能触发快捷键。

`restorePlayerFocusWhenCleared` 是模块内部的焦点回退：移除已聚焦的浮层或清空焦点时，
在偏好为 PLAYER 的情况下归还播放器焦点；移动到其他焦点目标时保留该目标。
拥有独立焦点 owner 的弹窗不经过页面 scope。

Android TV 使用自身的遥控器和焦点策略。
