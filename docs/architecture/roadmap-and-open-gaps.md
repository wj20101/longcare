# 路线图与开放问题

最后核对：2026-08-27

本文只记录仍然成立的后续工作。已完成的任务、逐次 CI 结果和历史方案通过 Git/PR/Issue 追溯，不在主文档中保留执行日志。

## 当前判断

- 护理端和销售端的主要用户链路均已实现。
- Debug 和显式验收构建可用。
- 生产发布被已知且可验证的厂商 SDK/配置问题主动阻断。
- 架构演进重点是缩小 `:app`、稳定平台生命周期和提高关键业务回归信心。
- 文档当前真相集已经收敛；后续应随实现更新，不再累积 design/plan/progress 副本。

## P0：恢复生产发布条件

Owner 涉及移动端、服务端和厂商，完成条件必须全部满足：

1. 服务端下发 QLZ 初始化所需配置，移除 Android 中固定测试 key 和 `QLZ_TEST_MODE=true`。
2. 替换 QLZ 1.3.0.2，确保可达网络链路不再触发弱 TLS trust manager finding。
3. 替换腾讯人脸 AAR，确保 ARM64 native library 满足 16 KB ELF/page alignment，并移除危险的全局 consumer ProGuard 规则。
4. 新厂商包通过真实设备的登录、身份核验、QLZ 蓝牙评估、报告、COS 上传和 Release shrink 回归。
5. `verify_vendor_sdk_release_readiness.sh`、生产配置门禁、Lint 和 production Release 全部通过。
6. 重新评估 Jetifier；只有全部相关厂商包 AndroidX-only 后才可关闭。

验收 Release 只用于联调/验收，必须显式标记，不得作为生产包分发。

## P1：`:app` 壳层收敛

建议按可独立验证的小切片推进：

1. `:feature:identification`
   - 已拥有默认人脸核验页面和大部分用例/状态。
   - 下一步评估迁移 `IdentificationScreen` 和 route-facing UI，保持结果 key 与补录兼容路径不变。
2. `:feature:login`、`:feature:home`
   - 迁移 route UI 前先固定 Home 子图 ViewModel owner、角色分流、隐私/协议和销售页返回行为。
3. `:feature:photoupload`、`:feature:servicecountdown`
   - UI 下沉时保持 app-owned 相机/Service/闹钟平台网关，不让 Feature 直接依赖 Activity 或实现类。
4. 销售能力
   - 建立独立 feature slice，拆分 oversized `SalesViewModel`，同时保持内部 `SalesNavigationState` 和应用级 Camera/WebView 返回行为。

所有迁移都受 legacy 新文件冻结、模块依赖白名单和 API visibility 门禁约束。

## P1：关键链路验证深度

优先增加高价值、少重复的验证：

- 隐私未同意、协议未勾选、会话失效和账号切换。
- 原生 NFC 与 R65C 外接读卡的开始/结束服务。
- 定位权限拒绝/恢复、前后台切换、退出登录、结束接口失败和任务移除。
- 人脸眨眼状态机、相机失败、服务端拒绝、会话失效和登记照缺失补录。
- 服务照片数量配置、私有 URL 解析、上传重试和受管文件回收。
- 倒计时、精确闹钟、通知权限、设备重启恢复和服务完成。
- 销售登记草稿、三张照片上限、QLZ 权限/Token 恢复、表单/报告 WebView。
- Room 迁移、WorkManager 重建和 app 更新下载恢复。

正常 Android CI 当前是 build-only 阻断策略；这些业务验证应在本地 `--full`、专项 workflow、发布验收或真实设备矩阵中明确承担，而不是被误认为已由普通 CI 覆盖。

## P1：Android API 37 与自适应

当前 targetSdk 36 通过 Activity 级兼容属性暂时保留 sw600dp+ 的竖屏限制。API 37 不再允许该 opt-out，因此升级前需要：

- 清点所有 app-owned 和 vendor Activity 的方向策略。
- 验证护理/销售页面在横屏、折叠、展开、多窗口和桌面窗口中的可达性与状态恢复。
- 验证 CameraX 预览、ML Kit 坐标、人脸取景、水印和全屏提醒方向。
- 将固定宽度内容改为有最大宽度的响应式布局，并保证低高度窗口可滚动。
- 在 tablet/foldable emulator 和至少一台真机上增加回归证据。

不要用新的兼容绕过代替适配。

## P2：导航与模块 API

- 统一 feature entry、route key 和 app navigation registry 的所有权；当前 registry 只含 login/home/identification 三项。
- 评估 Navigation 3，但必须先建立 route/payload/back-stack 等价测试。
- Navigation 3 迁移不与业务接口变化、模块大搬迁或 targetSdk 升级合并。
- 继续缩小公共 API，优先 `implementation` 和 `internal`，避免跨模块访问实现包。

## P2：工程与可观测性

- 定期生成 Baseline Profile 并验证关键旅程未退化。
- 构建耗时、质量快照和 CI 健康报告只输出到 `build/reports/` 或 CI artifact，不回写长期 Markdown。
- 保持 Gradle/AGP/Kotlin 升级可单独回滚，并运行 wrapper、workflow、Lint 和架构守卫。
- 第三方 SDK 升级需要记录 AAR 校验和、权限变化、Manifest merge、consumer rules 和 native ABI 结果。

## 当前不做

- 不因为模块目录名称不理想而一次性搬迁所有页面。
- 不通过关闭 Lint、增加全局 `-ignorewarnings`、放宽 exported component 或复用 debug 签名绕过生产发布问题。
- 不恢复相册选图、跨重启定位自动恢复或离线定位补传，除非产品明确改变现有规则。
- 不把内部功能验证入口升级为普通用户导航。

## 更新触发

完成任一条目后：

1. 删除或改写本文件中已不成立的 gap。
2. 同步产品概览、系统概览、页面地图、技术栈或质量门禁中的相关事实。
3. 长期架构取舍写 ADR；执行过程留在 PR/Issue。
