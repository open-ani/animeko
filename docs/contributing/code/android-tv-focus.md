# Android TV 导航焦点

`TvFocusBoundary` 描述当前可交互的内容子树。活动状态由导航目标决定，与转场进度和
Lifecycle 状态独立。边界始终保留同一个焦点组和内容布局；非活动内容仍可绘制退场动画，
但拒绝进入焦点组、拦截按键，并隐藏子树的无障碍操作语义。子边界同时受父边界约束。

导航和容器分别提供边界：

- `TvAniAppContent` 的 `rememberTvNavigationFocusDecorator` 使用目标 `NavEntry.contentKey`
  选择活动条目。内容身份来自注册的 entry provider；动画中保留的其他条目处于非活动状态。
- `TvMainShell` 的 `AnimatedContent` 只允许当前选中的内容页请求焦点。退出登录确认期间，
  页面边界处于非活动状态。
- `TvModalOverlay` 继承所在条目的边界。活动弹层阻止焦点移出，条目退场时释放此限制，
  允许导航目标接管焦点。

页面通过 `rememberTvFocusScope` 绑定所在边界。`request` 拒绝非活动边界的请求，`Resolver`
在边界失效时丢弃待附着请求，`requestPrepared` 同时取消数据或布局准备。重新成为活动目标
时，由页面提交当前需要的请求；已经取消的请求不会复活。直接使用 `FocusRequester` 的
记忆恢复也受焦点组入口限制。

初始焦点和记忆恢复的时机由对应页面策略控制。`TvFocusScope.InitialFocus` 与详情页恢复
仍等待 `RESUMED`；边界本身允许目标页在 `STARTED` 且布局就绪时请求焦点。边界不负责选择
默认控件、记忆条目，也不在动画结束时追加焦点请求。

`TvNavigationFocusUiTest` 使用真实 `NavDisplay`、保存状态装饰器和受控动画时钟，记录条目
生命周期、组合实例、焦点请求与得失。断言覆盖转场期间提前聚焦、方向键、晚到的旧页请求、
快速返回、待附着请求与异步准备的取消，以及嵌套弹层的退场。纯转场用例单独验证条目移除
前后的焦点稳定性。`TvNavigationRailUiTest` 另行覆盖主壳切页动画期间的焦点归属。
