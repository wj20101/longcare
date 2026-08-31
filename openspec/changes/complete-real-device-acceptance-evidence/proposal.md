## Why

`prune-deterministic-project-r8-rules` 与 `separate-startup-and-baseline-profile-semantics` 均只剩设备验收，但当前证据分散在手工 smoke、模拟器报告和不满足完整指标条件的旧真机之间，容易把“可运行”“报告格式正确”或部分厂商链路误写为完整验收。现在需要一套 fail-closed 的统一真机证据流程，在不改动厂商制品和用户行为的前提下，明确完成或继续阻断这两个剩余项。

## What Changes

- 新增统一的真机验收入口，先验证设备为非模拟器、当前目标 API 36、多核 `arm64-v8a`，并记录设备、系统、CPU、构建 SHA、电量和热状态；不合格设备不得进入收益或 Release 运行结论。
- 使用同一次显式 minified acceptance APK/AAB，在合格设备上按机器可读场景清单执行登录、类型安全导航/恢复、定位、身份与厂商核验、照片、倒计时、QLZ、视频呼叫和更新 smoke；每项必须记录通过、失败或因外部账号/订单/外设阻断，部分执行不得汇总为通过。
- 在同一台合格设备和同一构建输入上连续执行至少两轮四场景的 None/Profile 冷启动比较，保留原始 AndroidX JSON，归一化 TTID/TTFD，并按既有退化预算判定；模拟器和 API 29 以下设备只允许作为非验收诊断。
- 新增机器可读的证据清单与验证器，把设备资格、构建身份、场景完整性、禁止异常签名、两轮对称性和性能结论绑定起来；报告只写入 `build/` 或 CI artifact，不提交本机数据。
- 只有全部对应证据通过时，才允许回到两个原 change 完成最后任务；任一外部条件缺失时保持未完成/`unverified`，且明确列出下一次所需账号、订单、NFC/BLE 设备或厂商环境。
- 保持 production Release 的 QLZ/腾讯厂商 blocker 原样 fail-closed，不修改厂商 AAR、consumer rules、依赖、targetSdk、Navigation、WebView、数据库或启动初始化时序。

## Capabilities

### New Capabilities

- `real-device-acceptance-evidence`: 定义统一的真机资格、minified Release 业务 smoke、Startup/Profile 重复测量和机器可读证据闭环。

### Modified Capabilities

无。

## Impact

- 主要影响 `scripts/quality/` 的设备验收入口、场景/设备配置、证据验证器及其 fixture，以及 `build/reports/` 下的一次性报告格式。
- 复用现有 acceptance 构建、connected instrumentation、内部验证入口、Macrobenchmark、Startup 报告归一化和产物验证，不修改应用用户行为或业务/网络/存储契约。
- 外部依赖是一台满足 API 36、多核 ARM64 和真实硬件能力的受控真机，以及相应测试账号、护理订单、NFC/R65C、相机、定位、QLZ BLE 设备和厂商联调环境；缺失任一必需条件时验收保持阻断。
- 当前在线 MI CC 9e 为 API 28，官方说明该版本可能无法提供 TTFD，且不满足 API 36 smoke；当前模拟器只可提供旅程与报告格式证据，二者均不能完成本 change。
