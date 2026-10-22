# 高德持续定位测试资料（2026-10-22）

本目录由用户明确要求随测试分支提交，保留已完成测试的可审查证据与复现方案。它不是生产发布验收。请先阅读[完整历史测试报告](report.md)、[固定窗口独立对比](comparison-summary.json)、[小米生命周期](xiaomi-lifecycle-summary.json)和[华为汇总](huawei/summary.json)。

## 历史测试和当前分支必须分开

已执行的 A/B 与华为测试只覆盖 APK SHA-256：

```text
f9eb5bdcf67cb9ec30bd383a32a5bb33cf462dcf2718a0fdd3964dbabb2b5ed8
```

该 APK 为 1.0.7 / versionCode 75；源基准见 [base-commit.txt](base-commit.txt)，当时另有未提交修改，因此基准提交号不足以重建完全相同 APK。小米 A/B 各固定五分钟拔线锁屏，均为 0 次原始回调、0 次有效本地上报、0/5 分钟覆盖。华为只做接线锁屏，1/2 分钟覆盖，且存在 CPU 持续运行的 164.700 秒原始空档；进程中断后自动恢复失败，可见页面显式恢复成功。

**当前测试分支在上述测试之后又新增生产行为，历史数据不得作为它的验证结果：**

- CPU 锁每 15 秒续租、每次 180 秒超时：订单期间可持续持锁，不等于总持锁时长有界；功耗与锁屏行为尚未真机验收。
- 原始回调静默 45 秒后执行 `stopLocation → setLocationOption → startLocation`。这不是已证实能清空 SDK 内部缓存的受支持重置接口，也没有本报告的对照结论支持。
- 只读 review 发现候选风险：若 stop 成功后 setOption 或 start 抛异常，当前处理吞异常，逻辑仍可能标记 active=true，导致停止后不能正常续采。本次新增测试只覆盖成功路径，未覆盖该异常路径。
- 测试分支另包含远端版本调整；不能用新的 HEAD 或 versionCode 冒充历史已安装 APK 的构建哈希。未生成“当前候选已通过全部验收”的结论。

本次候选的本地检查和新 APK 哈希见[当前验证记录](current-candidate-verification.json)与[单测汇总](current-candidate-unit-results.json)。全量所选 JVM 测试1118项通过，构建和Lint通过；没有执行新候选的真机测试。

后续验证必须先确认上述风险与测试范围，不得把服务存在、CPU 运行或旧位置重复返回当作一分钟有效新点达标。

## 本地完整原始归档

原始归档文件名：`location-sdk-foreground-2026-10-22.zip`，仅保留在本地任务资料目录，没有远端下载 URL。

```text
archive SHA-256: 32aca5f2f08f38a5f8298b2940ce26e86081b73b5401b5d91d095139ad746bd9
installed APK SHA-256: f9eb5bdcf67cb9ec30bd383a32a5bb33cf462dcf2718a0fdd3964dbabb2b5ed8
SDK JAR SHA-256: b33e86dbc00ce2f661aa2a125a0c329831ffb50d22dcc62ecd67c01b0db38e73
```

Git 不重复存放 APK、大型 ZIP、构建目录、完整平台源码或 SDK 字节码、项目源码备份与工作区补丁。精确平台版本/源码 URL/内容哈希见[官方源码索引](source/official-source-index.json)，SDK 版本/官方接口 URL 见[SDK 索引](source/sdk-index.json)。

## 离线复核

以下命令仅读取本目录内已脱敏的历史记录，不连接手机、不重新测试：

```bash
cd validation/location-sdk-foreground-20261022
python3 summarize-device.py xiaomi-A
python3 summarize-device.py xiaomi-B
python3 summarize-device.py huawei
```

`analyze.py` 会重写对应目录的 summary.json。若需完整复算，请先复制证据到新的临时目录，避免改写提交中的历史记录：

```bash
trial_copy=$(mktemp -d)
cp -R xiaomi-A xiaomi-B "$trial_copy/"
python3 analyze.py "$trial_copy/xiaomi-A"
python3 analyze.py "$trial_copy/xiaomi-B" --compare-to "$trial_copy/xiaomi-A"
```

预期两组都为 rawCallbacks=0、uniquePositiveTicks=0、localSuccess=0；安装 APK 匹配，APS 前台状态符合各组。华为 wired 固定窗口的完整口径见其 summary，通用分析器只汇总全程，不替代该固定窗口。

## 复现脚本边界

[probe.py](probe.py) 与[华为捕获脚本](huawei/probe.py)是当时隔离测试辅助程序的可移植副本，调整仅包含 ADB、设备参数与输出目录获取。使用 ANDROID_HOME、ANDROID_SDK_ROOT 或 PATH 查找 adb，设备通过 `--serial` 或 ANDROID_SERIAL 指定，不再内置本机路径与序列号。脚本会初始化隔离偏好、启动真实高德、结束和恢复隔离状态；**不是纯只读命令**。重测必须安装与所测方案一致的 Debug APK，核实无真实恢复订单，从可见页面启动，并使用新的、忽略的 build 目录输出。测试前重新确认设备环境、权限与人工锁屏/拔线步骤。不得把这里的 original-state.json 用于恢复任何手机：它仅是离线分析所需的脱敏输入，不含原恢复备份。

例如（尚未执行，不代表当前候选已验证）：

```bash
export ANDROID_SERIAL=<本次授权设备序列号>
python3 validation/location-sdk-foreground-20261022/probe.py preflight   --out build/new-location-trial/A --sdk-foreground false --oem-proof <本次已核实的无限制证明>
```

固定窗口期间不连接或轮询手机。运行脚本会产生更详细的本地私有备份与系统转储，不得直接提交这些新输出。

## 脱敏与缺失范围

- 保留事件顺序、elapsed/uptime、SDK 源 tick、估计精度、错误、PID/client、测试订单号、服务前台状态、固定窗口和质量处理诊断，足够离线复算 A/B。
- 移除设备序列号、宿主绝对路径、账号/鉴权元数据、登录资料、坐标、用户 PIN、原偏好与私有恢复备份；测试计数提取为 probe-counters.json。
- 系统日志仅保留本应用相关的 idle/restart/扫描受限摘录；不提交完整 dumps、通知内容、其他 App 内容或厂商设置页面截图。完整证据仍只在本地原始归档/原 build 目录。
- 没有执行真实护理服务器/NFC 签到签退闭环、三星/荣耀现场验收、华为物理拔线测试或新 CPU 锁/45 秒刷新候选的真机验收。所有“本地成功”均是隔离仓库完成，不是远端护理接口成功，也不是位置真值证明。

[文件校验清单](file-manifest.json)只覆盖本目录脱敏副本；其哈希不等于原始归档文件的哈希。
