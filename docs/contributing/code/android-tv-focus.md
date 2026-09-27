# Android TV 导航焦点

`TvFocusBoundary` 描述当前可交互的内容子树。活动状态由导航目标决定，与转场进度和
Lifecycle 状态独立。边界始终保留同一个焦点组和内容布局；非活动内容仍可绘制退场动画，
但拒绝进入焦点组、拦截按键，并隐藏子树的无障碍操作语义。子边界同时受父边界约束。

导航和容器分别提供边界：

- `TvAniAppContent` 的 `rememberTvNavigationFocusDecorator` 使用目标 `NavEntry.contentKey`
  选择活动条目。内容身份来自注册的 entry provider；动画中保留的其他条目处于非活动状态。
- `TvMainShell` 的 `AnimatedContent` 只允许当前选中的内容页请求焦点。弹层遮挡由
  `tvModalUnderlay` 处理；关闭弹层属于局部焦点归还，不触发页面的初始焦点。
- `TvModalOverlay` 继承所在条目的边界。活动弹层阻止焦点移出，条目退场时释放此限制，
  允许导航目标接管焦点。

页面通过 `rememberTvFocusScope` 绑定所在边界，并安装一个 `Resolver`。每个 scope 只持有
一个请求，初始焦点、显式跳转、记忆恢复和异步准备共享此入口：

- `request(key)` 提交具名目标；`tvFocusAnchor` 登记对应节点的附着、布局和焦点状态。
- `InitialFocus(key)` 在条目成为活动目标时恢复记忆，缺少记忆时使用默认目标。
- `requestPrepared` 和带准备回调的 `InitialFocus` 允许页面等待目标数据、滚动 Lazy 容器，
  然后返回目标 key。需要先聚焦再播放滚动动画时，准备回调使用 `focusNow(key)`；
  后续动画仍属于同一个可取消请求。
- `Resolver` 在窗口获焦、目标已附着且具有有效布局时送达请求。条目可在 `STARTED` 的
  转场期间聚焦；送达成功后，动画收尾和 `RESUMED` 都不会再次送焦。

Lazy 重排时，同一个锚点 key 可以短暂对应多个节点实例。就绪状态按节点登记，旧节点卸载
只移除自身，替换节点需要完成自己的布局才能接收请求。

后续请求替换当前请求，方向键、确认键、菜单键和返回键取消在途请求。边界失效也会取消
请求及其数据等待、滚动动画；重新激活时由 `InitialFocus` 选择当前目标，已取消的请求
不会复活。边界将用户交互代数传递给所有后代，侧栏收到的按键也能取消页面内迟到的恢复。
没有导航边界的独立组件通过 `tvFocusNavSignal` 上报用户交互。

`TvFocusMemory` 只保存最后聚焦的身份并登记存活节点，通过 `tvFocusMemorable` 接入。
稳定 `memoryId` 支持节点重建；未提供 ID 时只支持同一节点的恢复。记忆对象由主壳调用方
保存在导航容器之上。`restore(memory, fallback)` 捕获当次身份，目标尚未就绪时仅送达一次
fallback，随后等待保存的目标；用户操作或导航离开会取消等待。记忆本身不请求焦点，
fallback 获焦也不会改写当次请求已捕获的身份。内容 tab 切换时主壳清除记忆。
导航入口可以仅登记节点，在执行入口动作时调用 `memory.remember(id)`；侧栏焦点经过
设置项时保留内容区的位置，实际打开设置页后才记住该入口。

页面只准备业务数据与滚动位置，窗口和节点就绪由框架负责。焦点逻辑不依赖页面生命周期；
轮播自动播放等后台暂停需求可以单独使用 `RESUMED`。

`TvNavigationFocusUiTest` 使用真实 `NavDisplay`、保存状态装饰器和受控动画时钟，记录条目
生命周期、组合实例、焦点请求与得失。断言覆盖转场期间提前聚焦、方向键、晚到的旧页请求、
快速返回、待附着请求与异步准备的取消、记忆目标重建与迟到，以及嵌套弹层的退场。
纯转场用例单独验证条目移除前后的焦点稳定性。`TvNavigationRailUiTest` 另行覆盖主壳切页
动画期间的焦点归属；`TvAccountNavigationUiTest` 验证弹层关闭后的账号入口恢复。
