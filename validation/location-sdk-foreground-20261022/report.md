# 高德前台服务与后台定位验证记录

记录日期：2026-10-22，时区 Asia/Shanghai。本记录汇总已完成的 SDK 前台服务试用方案、实际条件、脱敏证据、结果与限制；不是已经解决问题的声明。

**版本边界：以下结果对应固定 APK SHA-256 `f9eb5bdcf67cb9ec30bd383a32a5bb33cf462dcf2718a0fdd3964dbabb2b5ed8`。此后新增的生产 CPU 锁续租与 45 秒静默刷新未经过本报告真机验收，风险见 [README](README.md)。**

## 1. 历史已测版本的结论与状态

- 小米同 APK 两组固定五分钟拔线锁屏窗口：原始回调、新正源 tick、有效本地上报均为 0，分钟覆盖均为 **0/5**。SDK 自身前台服务已实际启用，本轮未观察到一分钟有效上报改善。
- 两窗口的进程与客户端身份均保留；有大量 CPU suspend，不能把“进程还在”解释为线程持续执行，也不能把没有新位置解释为业务任务已取消。
- 华为启用 SDK 前台服务后，接线锁屏两分钟仅 1 次原始成功回调与本地上报，分钟覆盖 1/2；另外存在 elapsed/uptime 均前进 164.700 秒的原始回调空档，不能把全部空档归因于 CPU suspend。
- 小米一次真实进程中断后，原业务恢复逻辑恢复原订单、新 SDK client 与两个前台服务，并恢复本地上报。华为相同类别的自动恢复失败，系统明确停止 idle 服务；随后可见页面显式恢复及换单成功。
- 未将 SDK 额外前台服务启用为生产默认；试用开关仅在 Debug 隔离入口，默认关闭。没有增加常驻 CPU 锁、周期重建、闹钟/Worker 保活或历史位置补传。
- 两机隔离订单与服务均已结束，测试偏好和私有事件文件已恢复，真实业务 DataStore 未变。小米按用户最新指示保留“无限制”和实际系统电池豁免；华为保留原系统设置。

固定窗口独立审计：[comparison-summary.json](comparison-summary.json)。小米功能与清理：[生命周期](xiaomi-lifecycle-summary.json)、[最终状态](xiaomi-final-cleanup.json)。华为：[完整汇总](huawei/summary.json)。

## 2. 目标、约束与判断口径

业务目标：服务期间按 Home 或锁屏后持续定位上报；保留订单隔离、签到/签退取点与系统服务中断恢复。关注每一分钟内是否存在一个新鲜、质量合格的实际上报。

本轮按用户授权试用高德 `enableBackgroundLocation()`，保留现有业务恢复逻辑与同一个定位客户端，核实 SDK 前台身份是否改善原始回调与有效上报。

约束：不频繁重建，不永久持锁，不重放旧点，不伪造来源时间，不同时修改 GPS/网络/电池配置；不以“配置已调用”替代实际生效证据。只使用隔离订单，不写真实护理记录。

统计分层：

1. **进程/服务存活**：PID、业务服务与 APSService 记录、生命周期事件。
2. **CPU 执行**：`elapsedRealtime` 与 `uptimeMillis` 差；和 Doze、厂商冻结分别判断。
3. **SDK 原始回调**：在 SDK 监听器入口、业务过滤之前记录，包含失败或空对象。
4. **独立源时间**：正源 tick 去重，记录源龄；接收时间不是新的采样时间。
5. **有效上报**：实际经过生产质量校验后，本地隔离仓库成功完成。不是远端护理服务器确认，也不是地面位置真值。

生产规则沿用：SDK 成功、允许来源/可信度、GCJ-02、非模拟/最后位置、服务精度不超过 200 米、持续上报源龄不超过 60 秒、会话与恢复边界正确；未为试用放宽。

每分钟固定桶覆盖与滚动最大空档分别统计。五个桶各有一个点也可能存在超过 60 秒的间隔。本轮两组都是 0 个点，不存在这一歧义。无回调的精度记录为空，不能解释为精度为 0 米。

## 3. 版本、设备及基线

| 项目 | 记录 |
|---|---|
| App | 1.0.7，versionCode 75，Debug |
| 工程平台 | compileSdk 37、targetSdk 36、minSdk 24、JDK 21 |
| 高德依赖 | `com.amap.api:location:11.3.101` |
| 实际核验 SDK JAR SHA-256 | `b33e86dbc00ce2f661aa2a125a0c329831ffb50d22dcc62ecd67c01b0db38e73` |
| A/B 与华为安装 APK SHA-256 | `f9eb5bdcf67cb9ec30bd383a32a5bb33cf462dcf2718a0fdd3964dbabb2b5ed8` |
| Android CLI | 1.0.16625370，已通过 version/info 检查 |
| 小米 | MI CC9e，API 28，测试设备标识已脱敏 |
| 华为 | STK-L21，API 28，测试设备标识已脱敏 |

小米 A/B 相同基线：用户已确认厂商“无限制”；系统实时白名单与应用运行记录均为豁免 true；系统省电模式 0；Data Saver false；系统提供器 `network`，GPS 未启用；同 APK、同网络配置、相同室内测试环境。未改变全局省电设置或使用模拟电池状态。

华为维持自身原设置：运行记录系统豁免 false，提供器 `gps,network`。华为不是小米的单变量对照，不能将两个品牌的计数直接比较为服务方案效果。

SDK 配置：`Hight_Accuracy`、持续模式、间隔 30000ms、HTTP 超时 30000ms、cache=false、onceLatest=true；不取地址。窗口内没有 option 改写。高精度选项不会替用户打开系统 GPS。

基准提交见 [base-commit.txt](base-commit.txt)。工作区此前已有未提交修改，不能仅凭提交号重建本次 APK；完整工作区补丁、试用相关补丁、APK 与源文件快照仅在本地原始归档留存，不重复提交到 Git。本测试分支另有后续修改，不能用该分支 HEAD 冒充本次实际安装 APK 的构建状态。

## 4. 本轮对比测试方案与实际执行

### 唯一干预

| 组别 | 业务前台服务 | 同一 SDK client 的 APSService | 通知 |
|---|---|---|---|
| A | 保留 | 保持绑定服务，未前台化 | 业务 ID 1 |
| B | 保留 | 通过官方 API 提升为 started foreground service | 业务 ID 1；SDK 独立 ID 9001 |

不是将自定义 Intent 传给普通业务服务来替代 SDK Binder，也没有删掉业务服务的 `startForeground()`。Android 对具体服务的前台晋升义务仍由该业务服务完成。

### 执行步骤

1. 检查无真实订单恢复、无现有应用服务/进程；备份隔离偏好与私有事件文件，记录真实 DataStore 哈希。
2. 安装同一 APK；A/B 开始前均读取实际安装 APK 哈希、系统白名单、定位提供器、省电与网络策略。B 与 A 自动比较，条件不一致即拒绝继续。
3. 仅初始化本地假订单与上报仓库，真实高德 SDK 仍按原隐私与初始化链路运行；`sdkForeground=false/true` 是唯一方案差异。
4. 从真实可见页面启动，确认真实首点和业务前台服务后按 Home。开始 B 前，系统记录必须实际显示 APSService `isForeground=true` 且 ID=9001。
5. 保存应用状态、系统服务与电池策略。用设备 `/proc/uptime` 和宿主 `time.monotonic()` 多次采样建立时钟映射，选往返时间最小的一次。
6. 用户手动锁屏、物理拔线并回复后，定义固定半开区间 `[start,start+300000)`。窗口内不连接、不轮询设备，仅使用宿主时钟等待。
7. 至少五分钟后提示重连并读取私有持久 JSONL、计数和系统记录。通过电池历史核对整个窗口均在锁屏放电后、重新充电前。
8. 离线分析固定窗口。重连后的回调/上传、动作取点、进程中断测试全部排除在五分钟结果之外。
9. 完成已授权的动作与一次真实进程中断恢复，正常结束，观察无追加上传、无服务和活动通知，再停止剩余空闲进程并恢复隔离资料。

脚本：[probe.py](probe.py)、[analyze.py](analyze.py)、[基础分析器](summarize-device.py)。脚本在固定窗口活动期间拒绝 ADB 调用；`confirm-unplug`、`remaining` 只使用宿主时钟。

### 关键调用方式

```text
python3 probe.py --serial "$ANDROID_SERIAL" preflight --out xiaomi-A --sdk-foreground false --oem-proof <已核实无限制截图>
python3 probe.py --serial "$ANDROID_SERIAL" prepare --out xiaomi-A
python3 probe.py --serial "$ANDROID_SERIAL" start --out xiaomi-A
python3 probe.py --serial "$ANDROID_SERIAL" home --out xiaomi-A
python3 probe.py --serial "$ANDROID_SERIAL" anchor --out xiaomi-A
python3 probe.py --serial "$ANDROID_SERIAL" confirm-unplug --out xiaomi-A --confirmed 已拔线
python3 probe.py --serial "$ANDROID_SERIAL" remaining --out xiaomi-A
python3 probe.py --serial "$ANDROID_SERIAL" reconnected --out xiaomi-A
python3 analyze.py xiaomi-A

# B 使用同一 APK；预检增加 --compare-to xiaomi-A，sdk-foreground 改为 true。
# A 恢复原测试偏好后，才准备 B；不在仍运行的 client 上反复切换。
```

`oem-proof` 指向已经观察并由用户确认的实际配置，不代表能用公开 API 自动读取厂商“无限制”开关。复验必须使用新的输出目录；不能复用归档 A/B 目录或将其中的原状态用于恢复其它测试会话。

## 5. 小米固定窗口结果

| 指标 | A：原方案 | B：启用高德前台服务 |
|---|---:|---:|
| 窗口起点 elapsedRealtime/ms | 2945587068 | 2946113803 |
| 窗口终点（不含）/ms | 2945887068 | 2946413803 |
| 时长 | 300 秒 | 300 秒 |
| 时钟映射误差约 | 48.88ms | 53.35ms |
| 原始回调/成功回调 | 0/0 | 0/0 |
| 新正源 tick | 0 | 0 |
| 有效本地成功 | 0 | 0 |
| 成功分钟桶 | 0/5 | 0/5 |
| 窗口内无成功跨度 | 300 秒 | 300 秒 |
| PID（窗口前后） | 6091→6091 | 6891→6891 |
| client（窗口前后） | 1→1 | 1→1 |
| 窗口内新建 client/改配置 | 0/0 | 0/0 |
| 业务 FGS 前后 | true，ID1 | true，ID1 |
| APS FGS 前后 | false，绑定服务 | true，ID9001 |
| CPU suspend 窗口下界约 | 270 秒 | 285 秒 |
| 持久事件覆盖整个窗口 | 是 | 是 |

CPU 下界由覆盖窗口的事件两端计算：`elapsed差 - uptime差`，再扣除窗口外时间；不是逐毫秒睡眠测量，也不支持判断 B 更耗电或更省电。考虑映射误差后的保守下界分别约 269883ms、284983ms。

电池历史独立核实：A 在窗口起点前约 3.549 秒熄屏，终点后约 108.126 秒才重新充电；B 起点前约 10.350 秒熄屏，终点后约 14.970 秒重新充电。CPU suspend 早于轻量 Doze，不能把二者等同。

完整结果：[A](xiaomi-A/summary.json)、[B](xiaomi-B/summary.json)。系统前台状态是窗口端点的实际观察，没有在拔线期间连续轮询；不将端点状态写成每一毫秒的证明。

### 动作、恢复与清理

- B 重连后原 client 自然恢复回调/上传，未以重建代替该窗口结果。
- 已有持续 client 时的隔离动作取点：475ms、SDK 估计精度 39 米，前后均 client1。动作日志屏幕关闭，准确范围是锁屏后的隔离动作，不是实际 NFC/签退接口闭环。两机诊断均为 `warm=false`、`warmRejection=ORDER`，未命中已有位置缓存；计时为动作外层端到端耗时。
- 一次真实 kill：PID6891→7672；恢复原隔离订单9000001，创建 client2，并实际恢复业务 FGS 和 APS FGS9001。
- 从新 `service_create` 到首次本地成功为 886ms；从 kill 前快照到首次新进程本地成功为3324ms，包含快照和命令派发，不能称为精确 kill 恢复耗时。
- 正常结束后，服务自然销毁、SDK 配对关闭；强制停止空闲进程之前，两个服务已为空、活动通知为0。随后171720ms观察内上传计数保持7→7。
- 原隔离偏好与私有事件文件恢复；真实 DataStore 哈希不变，无真实订单恢复身份。保留用户要求的电池基线，GPS 未改。
- 试用创建过 Debug 通知渠道；没有专门删除渠道。无活动通知不等于渠道已经删除，其最终持久状态未作确定声明。

## 6. 华为功能、恢复及失败记录

华为只做接线验证，没有物理拔线；原系统电池豁免 false 未修改。

| 项目 | 实际结果 |
|---|---|
| SDK 前台服务 | 实际进入前台，ID9001，与业务 ID1 分开 |
| Home 观察约72.58秒 | 1次原始回调、1次本地上报，同 client1 |
| 接线锁屏120秒 | 原始成功1、新正源tick1、本地成功1，分钟覆盖1/2 |
| 锁屏窗口最大边界空档 | raw 89.454秒；本地成功89.421秒 |
| 另一个原始回调空档 | 164.700秒；elapsed和uptime均前进164.700秒，订单检查继续 |
| 已有持续 client 的隔离动作 | 同 client1，4710ms、31米；屏幕关闭 |
| kill后自动恢复 | **失败**：新PID7323出现，创建/销毁诊断相隔1304ms，系统停止两个idle服务，没有新client |
| 可见页面显式恢复 | 成功：实际首页确认后按已有恢复入口，client2及两个FGS恢复 |
| 一次换单 | 9000001→9000002；旧client2配对关闭，新client3启用；旧单后续上报0 |
| 正常结束 | client3关闭，66.773秒无后续上报，服务/活动通知/进程为空 |
| 全程计数 | 12次raw成功、11个正unique tick、10次本地成功；不用于分钟达标结论 |
| 数据恢复 | 原偏好和日志精确恢复，真实DataStore及系统设置未变 |

### 自动恢复失败的证据及解释边界

系统先安排业务服务1000ms后重启，00:38:52.278明确记录停止两个idle服务。该日志早于新PID7323的 `service_create` 诊断事件（00:39:06.398，elapsed=989412180）：

```text
ActivityManager: Stopping service due to app idle ... APSService
ActivityManager: Stopping service due to app idle ... BackgroundLocationProbeService
```

随后确有 `service_destroy`（00:39:07.703，elapsed=989413484），两个事件相隔1304ms，期间没有创建SDK client或恢复上报。创建诊断在 `super.onCreate()` 和偏好提交之后打点，不能据其时间严格断言系统停止发生在方法入口之前。这证明系统主动停止服务记录。尚未证实华为 ROM 在该时刻具体为何判定受限；单凭 batteryExempt=false 不能唯一归因，AOSP 源码也不能替代厂商实际分支。

同一段系统日志还有明确归属本应用的锁屏Wi-Fi扫描限制：00:35:07.535记录 `startScan,pid:5908,uid:10131,appName:com.ytone.longcare`，下一毫秒记录 `Screen is off, com.ytone.longcare startScan is skipped.`，后续多次重复。可确认系统跳过了这些扫描请求，不能直接推导所有原始回调空档均由此造成。与CPU持续执行的164.700秒空档分别保留，不混为休眠单一原因。摘录见[本应用系统日志](huawei/sticky-recovery-relevant.log)。

- 日志中的 `-6m20s` 是服务记录创建以来的时长，不是 idle 持续时间或六分钟阈值。
- APS 曾被安排758566ms后重启：AOSP存在未完成 redeliver start item 的已交付时长×2增加重启最短等待的机制；同进程 attach 又可提前调度其他待恢复服务。因此不能写成“高德必等12分钟”。
- 可见显式恢复成功不能覆盖或改写自动恢复失败。
- 未执行冷动作新场景、华为物理拔线、不同华为电池策略对照或真实护理接口。

华为完整数据与证据清单：[summary.json](huawei/summary.json)。涉及系统停止的原始日志保留于本地华为目录；可分享归档只纳入本应用相关的摘录，排除其它 App 内容。

## 7. Android 与高德实现核验

以下平台源码固定为 `android-9.0.0_r1`，匹配本轮设备 API28 的平台代际；不声称 AOSP 与 MIUI/EMUI 的所有内部实现完全相同。精确 URL、SHA256 和行号保留在[源码清单](source/official-source-index.json)，字节码仅保存在本地原始归档；Git 仅保留[SDK 索引](source/sdk-index.json)。

| 已核验事实 | 对本轮的含义 |
|---|---|
| Handler延迟消息按uptime排程，CPU suspend时uptime停止 | 常规定时消息不构成CPU唤醒源 |
| PowerManagerService按实际CPU锁汇总维持suspend blocker | FGS或电池白名单本身不创建CPU锁 |
| 系统GNSS真实事件交付具有系统管理的锁 | 保护已产生事件的交付，不保证室内每分钟有新fix |
| 前台义务绑定具体ServiceRecord | APS前台化不能替业务服务完成其startForeground义务 |
| 多个FGS可以同进程存在 | 没有仅凭服务数量认定冲突；通知仍须明确归属 |
| stopInBackground检查started服务和后台启动模式 | FGS不是永久后台恢复保证；华为出现该类系统停止 |
| SDK自身FGS提升APS为started foreground服务 | Java已审路径未发现额外CPU唤醒或改采样策略 |
| SDK网络周期AlarmWrapper实际转Handler.postDelayed | 类名含Alarm不代表使用硬件唤醒闹钟 |
| SDK默认selfStart=true，APS返回START_REDELIVER_INTENT | 可重投SDK服务命令，不能代替护理订单/鉴权/上传会话恢复 |

高德公开说明：应用已有前台服务通知时无需再开启SDK后台通知接口；启用后台能力不等于开始定位；`setInterval` 是发起请求间隔，未承诺每分钟一个独立合格点和一次HTTP成功。

Java字节码中未发现显式WakeLock/AlarmManager标记的扫描，仅覆盖该JAR Java路径，不能排除native、反射、系统provider等行为，不可夸大为SDK所有层都没有任何锁。

官方依据：

- [Android 9 Handler](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/core/java/android/os/Handler.java#662)
- [Android 9 SystemClock](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/core/java/android/os/SystemClock.java#51)
- [Android 9 PowerManagerService](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/services/core/java/com/android/server/power/PowerManagerService.java#2572)
- [Android 9 ActiveServices：前台义务](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/services/core/java/com/android/server/am/ActiveServices.java#1194)
- [Android 9 ActiveServices：idle停止](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/services/core/java/com/android/server/am/ActiveServices.java#749)
- [高德后台定位API](https://a.amap.com/lbs/static/unzip/Android_Location_Doc/com/amap/api/location/AMapLocationClient.html)
- [高德参数](https://a.amap.com/lbs/static/unzip/Android_Location_Doc/com/amap/api/location/AMapLocationClientOption.html)

## 8. 此前相关证据与避免重复试验的边界

| 已有观察 | 结果及边界 |
|---|---|
| 小米原方案真实拔线锁屏 | 固定5分钟0/5，同进程/client，无重建；旧A目录缺安装后APK哈希，不与本轮宣称二进制完全一致 |
| 临时CPU持锁隔离对照 | CPU持续执行，但5分钟本地成功只能界定1～3次；中段logcat缺失，不能给精确分钟桶。相关临时锁和代码已删除 |
| 仅系统电池豁免 | 小米固定5分钟0/5；不能说明所有机型豁免都无效 |
| 厂商无限制后 | 仍0/5；起始人工标记exempt=false与实际trace/系统true冲突，已更正为两项放宽的组合，不能称OEM单变量 |
| 华为SDK内部取证 | 既有记录曾确认新网络结果经小漂移过滤返回旧tick、未交付应用；只解释被取证的那一次，不覆盖所有现场 |
| 历史SDK回退对照 | 11.2.100表面10/10但17次重复旧tick，不能靠回退或仅UTC刷新宣称达标 |

这些历史记录不是本轮重新执行的结果。完整历史原始目录只在本地保留；本目录只保留所需历史汇总，历史边界也同步维护于[工程风险说明](../../docs/analysis/project-review.md)。

## 9. 改动、验证与资料保存

本轮最小接入：具体 `SdkAmapClientHandle` 增加两个internal官方API直通；不修改公共`AmapClientHandle`。Debug `TracedClient` 按持久试用参数首次启用一次，销毁前配对关闭，finally仍销毁原client；系统恢复新client读取相同参数。记录`requested`而非假定成功，实际FGS以系统状态验证。新增通知资源仅在Debug。

独立代码review未发现新增问题；确认SDK同一Handler上的enable/disable/destroy顺序、异常清理、恢复重新启用和默认关闭路径。没有增加生产开关、定位客户端、保活框架或后台权限。

验证记录：

- 定位模块211项单测通过；App Debug与androidTest APK构建、App Lint通过。
- 全量 `preflight_local.sh --full` 通过；所选模块1117项测试，失败0、错误0、跳过0。
- Lint既有接受项为Aligned16KB、TrustAllX509TrustManager，未扩大豁免。
- 本轮SDK前台服务在小米、华为真实SDK验证；未将既往模拟器UI测试冒充此次SDK前台服务或物理拔线验收。

本次 Git 保存本报告、A/B 配置与固定窗口、脱敏事件、必要服务状态、独立审计、两机生命周期与清理、可移植脚本、[测试摘要](unit-results.json)及源码精确 URL/哈希索引；此前对照汇总保存在 `history/`。完整构建日志、实际 APK、补丁和平台/SDK 源码证据保留在本地原始归档，索引与校验值见[README](README.md)。不提交用户 PIN、登录资料、坐标、原偏好恢复备份、原 DataStore、完整系统转储、厂商页面截图或其他 App 信息。必要计数由 XML 提取为各设备目录的 `probe-counters.json`。

后续仍未确认：远端三星/荣耀/Vivo现场根因、所有CPU空档的唯一原因、华为本次idle判定的具体ROM条件、能满足锁屏一分钟合格新点的生产修复。不得据本轮结果宣称这些问题已解决，也不以额外FGS长期叠加作为未经验证的修复。
