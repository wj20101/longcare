## Context

归档说明：本文件保留当时的设计决策；其中消息桥、来源限制和能力提示已由后续 complete-device-h5-evaluation-flow 的最小 NativeBridge 方案替代。归档规格已按最终确认行为收敛，当前实现不包含这些旧分支。

动机见 [proposal.md](proposal.md)。现有 `SalesEvaluationGuideScreen` 将握持图和检测状态分为两张卡片；`QlzEvaluationUiState` 有五指与采样进度，但没有准备倒计时。`WebViewScreen` 是表单、报告和其他网页共用容器，没有 JS 桥；`WebViewRoute` 只有 URL 和标题。现有 Navigation 3 source-bound navigator 已具备来源 entry 校验，应复用而非另建返回机制。

蓝湖已登录并逐张核对，三张均为版本 1（许伟，9 月 14 日 21:05），项目 ID 为 `54e159bf-42c8-4c47-96cb-159af2d1b305`：

| 页面 | image_id | 视觉基准 |
| --- | --- | --- |
| 检测 | f02d1cac-4384-458f-84e5-ca2922ec3caf | 手握线稿、五个竖向圆角接触灯、请按照示意的方式握住设备 |
| 检测-2 | 833cf53f-5ae2-46d5-b6e3-5a055ab07cd7 | 同一握持图、五灯全绿、绿色握持完成提示、倒数 5 秒 |
| 检测3 | 3b3c1d92-77fe-44bb-8188-869739b2d3a4 | 蓝色沙漏、绿色圆角进度条、淡紫轨道、百分比、检测中 |

三稿统一白色返回箭头与「手握检测」标题、上蓝下浅的渐变背景、单张白色圆角卡片。检测3标注的画布为 750×1341 px；这是源稿像素，不可直接当 Android dp。实施时从标注取得颜色、间距、圆角及可导出素材，并核对现有 `sales_evaluation_instruction.png` 是否为同一原图。

资源整理追加约定：手握、沙漏位图统一放在 `app/src/main/res/drawable-xxhdpi`，采用当前 WebP 文件；
本次仅迁移目录，不重新采样图片，不改变资源名称或 Compose 显式 dp 布局尺寸。

实施核对：Sketch 标注 `ArtboardScale = 2`，以 375 dp 基准换算。白卡 678×682 px、圆角 16 px；
标题 36 px、正文 32 px；接触灯 39.09×49 px（绿 `#63E544`、灰 `#DBDBDB`）；
进度轨道 496×38 px、`#E5EDFF`；背景 `#468AFF` 渐变至 `#F6F9FF`。
已以原始导出线稿替换旧握持图片，并加入 142×241 px 透明沙漏切图；动态指示仍由 Compose 绘制。
原稿的“请不要松开收”和“检测中，”分别修正为“请不要松开手”和“检测中”，46% 只作 mock 对照值。

进度条采用完整浅色圆角底轨叠加绿色自绘填充，避免默认 Material 分段圆角留下白色缺口或扩大低进度。
填充宽度直接乘真实比例，0% 不绘制绿色、100% 完全覆盖；文字以整数采样数计算，避免浮点截断把 29% 显示为 28%。

## Goals / Non-Goals

**Goals:**
- 以原生 Compose 重建布局，不把整张设计截图当 UI；真实接触/进度驱动可访问的动态元素。
- 用来源和生命周期受限的桥接实现精确 JS 契约，并验证真实 WebView 调用。

**Non-Goals:**
- 不改扫描/连接页、完成页、服务器托管 H5 内部布局；不重做整个销售模块。
- 不延迟 SDK 启动、不制造测量数据、不把关闭事件解释成业务提交成功。
- 不迁移 Navigation 2，不变更助手或现有 QLZ 上传/生产门禁。

## Decisions

### 1. 展示状态与设备状态分离

在现有状态上派生「握持提示 / 准备倒计时 / 检测进度」展示。五指全部接触且尚无有效检测进度时进入 5 秒准备展示；期间任一接触丢失即取消。倒计时结束只切换展示，不调用 SDK 开始/完成/上传。首个有效采样进度提前到达时立即展示真实进度，不为凑足 5 秒隐藏事实。进入进度阶段后不因接触抖动重复启动准备倒计时。

倒计时按会话管理、使用可测试时间源，重组不得重启；退出、后台、断连、充电暂停和错误取消计时。前台恢复尚未进入采样时重新根据可靠接触状态准备，不沿用后台倒数。以 Demo/AAR 事件顺序核对映射；如果真实协议与此展示约定冲突，先更新本 change，不改 SDK 采集协议来迎合设计。

保留现有异常/充电/上传状态及恢复操作，正常三态不显示旧的重复状态卡、设备名称和无关辅助文案。未知总数不虚构百分比；100% 采样不表示上传成功。

AAR 1.3.0.5 的 `ConnectDeviceCallback` 公开 `onCheckStart`、`onCheckState(boolean[])`、
`onCheckPro(int,int)` 和 `onPowerChange`，没有准备倒计时接口；`ConnectDeviceHelp` 分别转发五指和进度。
因此计时由 `QlzGripPreparation` 派生，不能视为设备端采样开始信号。

### 2. 素材与自适应

优先使用蓝湖原始手握/沙漏素材，动态灯和进度用 Compose 绘制。小屏和大字体允许内容滚动；大屏限制卡片宽度并居中，保留系统栏安全区。不固定全屏高度，不隐藏返回或异常恢复操作。先确认资源引用后删除被替代的旧资源。

### 3. 所有 H5 统一容器与关闭接口（用户追加确认）

删除表单专用 `evaluationForm` 标记及 `onOpenEvaluationForm` 动作，统一使用普通网页导航。表单、报告、普通网页、协议及隐私网页弹窗均注册相同关闭桥，不根据标题或 URL 文本猜测用途。隐私网页弹窗复用 `WebViewScreen`，开启 JavaScript，关闭动作仅映射到当前网页弹窗的 dismiss，不触发隐私同意或拒绝。页面销毁时释放桥和 WebView；按路由请求身份而非当前页面 URL 决定加载，重组不得重载重定向后的网页。

### 4. 保持单一公开方法，采用来源受限消息桥

使用 AndroidX WebKit `addWebMessageListener` 配合 `addDocumentStartJavaScript`：向各 H5 入口的可信源注入 `window.NativeBridge.closeWebView()`，内部转发固定关闭消息，不暴露账号、Token、文件、任意导航或其他原生能力。桥和策略使用通用命名 `WebViewCloseBridge` / `WebViewClosePolicy`，删除评估专属实现名。

可信源取自经过校验的网页入口 HTTPS URL 的精确 scheme/host/port，限制为当前容器的来源集合；不加入重定向目标或通配符。监听器同时验证来源、顶层 frame、固定消息和当前容器生命周期。只允许当前仍有效且已恢复的容器在主线程关闭一次；路由容器额外验证 source-bound entry 位于栈顶；重复/迟到消息忽略，不 `Activity.finish()`。

运行时检查两个 WebView 功能。缺失时不降级到全 frame 的 `addJavascriptInterface`，保留正常页面及原生返回操作，并提示可更新系统 WebView；非 HTTPS 评估页面不注入桥。不放宽 TLS/混合内容限制。实施时核对稳定 WebKit 版本后锁定在 version catalog。

官方知识库稳定通道核对后已锁定 AndroidX WebKit 1.17.0；依赖解析及编译通过。

已使用 android-cli 官方知识库核对：
- [原生 API 与 JavaScript 桥](https://developer.android.com/develop/ui/views/layout/webapps/native-api-access-jsbridge)：来源规则、主 frame 判定、文档起始脚本与功能检测。
- [不安全原生桥风险](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)：传统接口暴露给所有 frame，不能用当前 WebView URL 证明调用方可信。

不采用给所有网页注册传统桥、通过页面标题猜测用途、重复点击返回或清空导航栈等替代方案。

### 5. 验证

单测覆盖状态映射、倒计时取消/重入、URL 来源规则和关闭去重。Compose mock 覆盖三态及错误/充电/上传，含小屏和大字体截图对照。真实 WebView instrumentation 通过本地受控 HTTPS 测试内容执行指定 JS 方法，覆盖同源顶层成功、iframe/跨源拒绝、重复/销毁后回调及返回原页面；测试数据和桥接测试宿主只放测试源集。

隔离模拟器验收中，Android CLI 截图成功并与三态设计核对；其 layout 服务返回无法识别的响应，
因此结构、可访问性和短屏滚动由 Compose 语义断言验证，进度填充使用像素测试验证。
不把 layout 服务失败记为通过，也不把 mock 结果代替真实 BLE/服务端 H5 验收。

### 6. WebView 渲染进程退出（用户追加确认）

WebKit 新检查发现通用网页和既有隐私弹窗均缺少 `onRenderProcessGone`。两个容器收到该回调后
返回已处理、停止加载提示，以原生异常说明替换网页；共用客户端在回调中立即移除并销毁失效实例，
不依赖后台可能暂停的 Compose 帧。正常页面销毁仍由 AndroidView 的释放回调清理；
不复用、不自动循环重载、不自动同意隐私政策或退出应用。评估桥同步失效，原生返回仍可用。
隐私弹窗按用户追加确认启用 JavaScript，复用通用网页容器及关闭桥。仅关闭网页，不改变同意状态。用真实 JS 和受控渲染退出回调验证关闭、释放与返回。

安全审查与用户确认的局部例外：WebKit 1.17.0 `RenderProcessGoneDetector.visitConstructor` 对父类构造调用
无条件报告，即使子类已实现回调。仅在不可继承的 `ManagedWebViewClient` 上豁免该误报并写明移除条件；
其回调明确返回 `true`，两个容器均有移除、销毁及原生返回测试，评估桥同步失效。
不修改全局 Lint 配置、warning allowlist 或发布门禁；后续 WebKit 修复检查器后移除该局部注解。

## Risks / Trade-offs

- [设计稿有视觉笔误或示例值] → 五指和 46% 等示例必须由真实状态替换；文案按确认设计实现，疑似笔误在实施前核对。
- [SDK 无倒计时回调] → 倒计时仅表示准备展示，真实进度优先，不宣称能控制设备计时。
- [H5 跨域跳转或旧 WebView] → 不自动扩大白名单；正常返回可用，真实 H5 验收单独记录能力/来源缺口。
- [健康数据和凭据泄露] → 关闭消息不携带业务数据，不记录完整评估 URL、Token 或客户信息；mock 不访问真实服务。
- [大量已有未提交更改] → 仅修改本 change 涉及区域，保护 Navigation 3 和 QLZ 已有实现。

## Migration Plan

先完成 UI 状态与 mock，再接入统一容器和安全桥，最后做 Navigation 3/WebView 回归和文档同步。无数据库、账号迁移，不新增旧用途标记兼容分支。回滚按本 change 文件片段恢复展示和桥接，不回退 Navigation 3、不清除用户数据。
