## 1. 最小发布策略调整

- [x] 1.1 核对 master 基线、用户风险接受和当前检查，调整现有 production/vendor 脚本，将确定的已接受事项改为明确警告；增加脚本正负用例验证已知风险不阻断、非法模式/缺失输入/未允许问题仍失败，不打印凭据。
- [x] 1.2 同步 Release workflow、质量门禁注册和当前发布文档/上下文，保留正式签名、Lint、导出组件和隔离检查；通过 workflow 守卫、local-fast、OpenSpec strict 和 diff 检查，不新增全局忽略。

## 2. 正式混淆包验收

- [x] 2.1 审查当前 R8 配置及网络模型/导航序列化/JavascriptInterface/JNI 等实际调用路径，运行完整本地回归；以单测报告和具体引用确认，不关闭压缩或无依据增加 keep。
- [x] 2.2 使用合法正式签名构建 production APK/AAB，关闭 acceptance 与 unsigned/debug fallback；核对 R8 mapping、签名、Manifest、助手隔离和版本，记录最终提交与 APK 哈希。
- [x] 2.3 API 24 与 API 37 在最终混淆目标包运行实际 SDK 消息/Gzip 专项，核验测试包无运行库副本及受控失败可被识别；完整 13 项继续由 JVM/Debug 覆盖，不冒称混淆测试全 API。
- [x] 2.4 覆盖安装保留数据，在最终混淆包核验冷启动/重启、登录态和导航恢复、销售列表/详情解析、报告 H5 关闭及返回、原生等级；检查崩溃/ANR/类缺失/字段缺失日志。记录可用护理测试资源下的验证范围及任何缺口，既有 QLZ 异常场景不得无证据标为完成；项目侧失败须修复重测后才能交付。

## 3. master 交付

- [ ] 3.1 审查最终差异并按已授权范围提交、推送、合入 master，核对最新候选及合入 CI；交付正式 APK 路径、哈希和具体验收结果，区分已接受风险与未测场景，不自动向商店/更新服务分发，不提交日志、缓存或凭据。

## 验收记录

- 首轮基于 `57eb5e46` 的发布脚本/文档差异，当时业务源码、AAR、版本目录和 R8 规则未改。8 项发布策略脚本测试、workflow 守卫、local-fast、完整 preflight、双应用 Lint、App warning allowlist、导出组件、助手隔离及 OpenSpec strict 通过。完整 preflight 的 Gradle 单测复用未变源码的有效缓存，未冒称全部重新执行。
- `analyzeReleaseR8Config` 完成并输出 HTML/PB；技能转换脚本缺失，使用 Gradle 原生报告及实际 mapping 核对，不虚构评分。反射入口、Moshi 生成适配器、导航 serializer、JNI 与 `NativeBridge.closeWebView` 保留。未新增 keep/ignorewarnings；厂商自带全局 ignorewarnings 仍作为已接受风险存在。
- production 构建显式设置 `release.production=true`、`release.acceptance=false`，两个 unsigned fallback 均关闭；APK 为 `com.ytone.longcare` 1.0.6(59)，不可调试，代码/资源压缩启用。签名 SHA-256 为 `dfe66e04e43943a356d9db1a1ad81fff75e185caf6b43d2a392edada908c3562`。
- APK SHA-256：`b0d282352b6003f604215ddfbfcb8b9439076bd6b8aacce057d253d8e54efd06`；AAB SHA-256：`23d5892673a410734720fd18b61880186f63a5e8db8e4fa23c04f37f6b9e63ed`。交付提交在最终合入时补充关联。
- API 24 模拟器及 API 37 Pixel 覆盖安装后，实际 R8 消息/Gzip 专项各 8 项通过，0 失败/跳过。测试 APK 不定义厂商消息或 Google Protobuf 实现类；受控失败返回 FAILED 和 code 0。完整 13 项 JVM/Debug 证据沿用未变代码的本轮 Protobuf 验收，不将 8 项称为全 API 验证。
- 首轮真机冷启动和客户 A 级详情正常，但实际后台杀进程后（PID 已变化），客户详情停留“暂无客户信息”。定位为已保存 ID 未触发详情重查，不是已证明的 R8 裁剪。按 2.4 修复后须重新执行完整回归、production 构建和设备验收；上述首轮产物不是最终交付依据。
- 最终修复只补充详情页按保存 ID 重查，增加 4 项恢复回归；完整 preflight 通过，App 390 项、助手 34 项单测无失败，详情/结果/表单恢复专项 16 项通过。最终发布策略脚本 9 项、workflow 守卫、local-fast、双应用 Lint 及 OpenSpec strict 通过。
- 最终 production APK/AAB 已在修复后重新构建，签名、版本、不可调试及压缩配置与上述一致。APK SHA-256：`00b8dda8158ee9ee3a246cb82317f332f11ec1ae90262418bb793a85c14a8da0`；AAB SHA-256：`9096a69893d13cec45c8244681c8691fb81c54bc7d81316615c669375df98523`。对应候选提交在 PR 中关联，合入后补充最终提交。
- 最终混淆包覆盖安装到 API 24 模拟器和 API 37 Pixel，实际消息/Gzip 专项各 8 项再次通过，测试包无 Google Protobuf/厂商消息实现副本；受控失败证据沿用相同 runner 的首轮负向检查，完整 13 项 JVM 测试包含在最终完整回归中。
- Pixel 最终产物冷启动保留销售登录态；后台杀进程后 PID 从 18933 变为 19156，客户详情自动恢复并重新显示 `ProtobufTest0919` 的 A 级。已有客户报告 H5 展示正常，点击 H5 自带左上角返回关闭网页，回到原生详情仍显示 A 级；系统返回首页、我的客户列表解析正常。当前进程日志未出现 FATAL/ANR 或类、方法、字段缺失异常；退出记录为安装、测试及主动后台杀进程。未新增或提交业务数据。
- 本轮没有可用的护理专用测试账号/订单，未执行真实护理签入签退；未重新执行 QLZ 硬件异常矩阵。既有正常真实检测/问卷证据仅作为未改 SDK 调用路径的补充，不将未测场景标为通过。已接受厂商风险仍未修复，不能承诺任意设备或未覆盖业务绝无崩溃。
