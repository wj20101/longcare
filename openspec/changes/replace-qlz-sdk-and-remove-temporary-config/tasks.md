## 1. 固定外部输入与安全基线

- [ ] 1.1 从受控厂商来源取得 QLZ 修复版 AAR、版本/安全说明、依赖清单和 reset/reinitialize 契约，记录 SHA-256 到现有 QLZ 集成文档，并验证文件不是旧 1.3.0.2 或其重命名副本；输入不齐时停止后续替换任务。
- [ ] 1.2 与服务端固定 `/V1/System/Config.thirdKeyStr` 中 QLZ appKey/环境的唯一 JSON 字段和值域、test/production 配置及部署顺序，用脱敏响应 fixture 验证旧客户端可忽略新增字段且响应不含 `appSecret`。
- [x] 1.3 在替换前保存旧 AAR hash、dependencyInsight、Lint `TrustAllX509TrustManager`、merged Manifest、consumer rules 和 production guard 的脱敏基线到被忽略的 `build/` 或 CI artifact，并验证证据能单独定位 QLZ 而不把腾讯人脸结果混入。
- [x] 1.4 用 Android CLI `android docs search/fetch` 复核当前蓝牙权限、Network Security Configuration 和安全网络建议，将适用约束落实到测试/验收条件，并验证没有以旧 targetSdk 行为或记忆推断替代当前官方事实。

## 2. 建立服务端 QLZ 配置契约

- [ ] 2.1 在 Android-free model/domain 边界增加 QLZ appKey、`test|production` 环境和配置有效性模型，扩展 `ThirdKeyReturnModel` 的精确 JSON 映射，并用序列化测试验证新增字段、未知字段兼容、缺失值和非法环境。
- [ ] 2.2 扩展系统配置解密/响应处理测试，验证加密 `thirdKeyStr` 能得到 QLZ 配置、解析失败保持 fail closed、日志不含 key，且原有高德/腾讯人脸字段不回退。
- [ ] 2.3 在 `:core:domain` 定义销售评估 SDK 配置 provider，在 `:core:data` 基于 `SystemConfigManager` 实现显式刷新/缓存读取，并用 focused tests 验证成功、网络失败、空配置和取消传播。
- [ ] 2.4 为 provider 结果加入当前登录会话或配置 generation，使退出、3002、换号和系统配置刷新可使旧结果失效，并用跨会话测试验证旧 appKey 不会返回给新会话。

## 3. 重构 QLZ 准备状态与构建策略

- [ ] 3.1 从 Gradle/BuildConfig 删除固定 `QLZ_SDK_KEY` 和固定实际模式值，改为不含凭据的测试环境许可策略，并用 Gradle/unit fixture 验证 Debug 与显式 Acceptance 可接受 `test`、默认 production 只接受 `production`。
- [ ] 3.2 在 app-owned QLZ 边界增加可替换的厂商 facade 和互斥准备状态机，按“刷新配置→校验环境→初始化→设备 ID”顺序执行，并用 fake 验证并发调用只初始化一次且失败不会留下 Ready 状态。
- [ ] 3.3 将 `SalesEvaluationDeviceGateway` 与销售准备流程改为调用可挂起的设备准备操作，保持 ViewModel 不接触 vendor/config 实现，并用测试验证取得设备 ID 后才请求原样的 `/V1/Sale/GetCheckToken`。
- [ ] 3.4 让 UI controller 只在当前会话的准备 fingerprint 仍有效时打开 SDK 页面；配置变化时按厂商支持契约 reset/reinitialize，无法安全切换时拒绝，并用 fake 覆盖同配置复用、换 key、换环境和 reset 失败。
- [ ] 3.5 接入退出、会话失效和换号的 QLZ 专项失效钩子，清除初始化 fingerprint、设备状态与内存 Token，并用 focused tests 验证下一账号必须重新获取配置和 Token。
- [ ] 3.6 统一配置/初始化/Token 错误的脱敏映射，验证用户只看到可重试业务文案，日志与异常文本不包含 appKey、Token、设备 ID、URL 或服务端内部详情。

## 4. 替换并审查厂商二进制

- [ ] 4.1 将受控修复版 AAR 加入 `app/libs` 并从依赖中删除旧 1.3.0.2，校验声明 hash 与实际文件一致，再用 APK/AAB 解包证明旧类库文件不再打包且新版本唯一。
- [ ] 4.2 仅在 QLZ adapter 内适配新厂商 API、初始化/reset 和回调差异，删除已确认只服务旧版的兼容代码，并用编译与 adapter tests 验证销售 Feature/Core 没有新增 vendor 类型依赖。
- [ ] 4.3 审查新 AAR 的 Manifest、权限、ABI、classes、consumer rules 与传递依赖，运行 Lint/字节码模式检查证明无 QLZ 信任任意证书或主机校验绕过；证据不足或仍命中时保持 P0 阻断。
- [ ] 4.4 移除 QLZ 专用 cleartext 域和旧版网络例外，检查 Debug/Acceptance/Release merged Network Security Configuration，验证 production 没有依赖 QLZ HTTP 的可达路径。
- [ ] 4.5 对新包运行 Debug/Release/Benchmark dependencyInsight 和 module/API 守卫，记录 protobuf/Jetifier/权限差异到既有后续 change，验证本 change 只加入厂商明确要求的依赖而不顺带实施独立整改。

## 5. 补齐自动化契约与安全测试

- [ ] 5.1 运行并扩展系统配置、ThirdKey、provider 和 API JSON contract tests，验证 QLZ 新字段唯一、`GetCheckToken` 请求/响应字段不变、旧 fixture 继续解析。
- [ ] 5.2 为 QLZ facade/状态机覆盖缺配置、非法环境、production 收到 test、初始化异常、并发准备、配置切换、reset 失败和取消传播，并验证每个失败都保持 fail closed。
- [ ] 5.3 扩展销售 ViewModel tests，验证成功顺序、BLE 权限拒绝/恢复、Token 最多刷新一次、取消/关闭不算完成、完成后只从 LongCare 客户详情回查报告。
- [ ] 5.4 增加退出、3002、换号和同一会话重入测试，验证旧配置/设备状态/Token 不跨会话，同会话只允许复用已验证初始化且每次评估取得新 Token。
- [ ] 5.5 对源码、资源、BuildConfig、生成配置、日志 fixture、APK 和 AAB 执行 secret/固定 key 扫描，验证不存在 `appSecret`、旧固定 appKey 或诊断泄露，并确认没有新增 keystore、waiver 或 debug 签名 fallback。

## 6. 更新门禁并验证构建产物

- [ ] 6.1 更新 production config guard 及其负向 fixture，独立阻断固定 QLZ key、固定测试模式、旧 AAR/hash 和 production/test 环境混用，并验证 QLZ 合法输入通过而未知参数继续 fail closed。
- [ ] 6.2 更新 vendor readiness 与 Lint waiver 的 QLZ 范围，移除仅属于旧 QLZ 的 waiver、保留 COS/腾讯人脸规则，并用合成 Lint fixture 验证弱 TLS 回退会被阻断。
- [ ] 6.3 运行 `bash scripts/quality/preflight_local.sh --full`、受影响模块 tests、`:app:lintDebug :app:assembleDebug :app:bundleDebug` 和 warning allowlist，验证新增代码、依赖与静态门禁全部通过。
- [ ] 6.4 用 `-Prelease.production=false -Prelease.acceptance=true` 构建 minified Acceptance APK/AAB，验证签名结构、R8/mapping、Manifest、ABI、Baseline Profile 和 acceptance metadata；不得用该结果宣称 production ready。
- [ ] 6.5 分别运行 production config 与 vendor readiness，验证输出不再包含任何 QLZ 根因且仍只因腾讯人脸 16 KB/R8 独立 P0 失败；出现其他根因时不得标记本 change 完成。

## 7. 服务端部署与真实设备验收

- [ ] 7.1 服务端先在测试环境、再在生产环境部署加密 QLZ 附加字段，验证旧客户端行为不变、新客户端缺字段时明确失败、两环境配置互不混用且响应始终不含 `appSecret`。
- [ ] 7.2 在显式 Acceptance 包、受控销售账号和 BLE 检测设备上完成客户选择、配置初始化、设备 ID、Token、权限、连接、进度、完成和报告回查，记录包版本/SDK hash/环境到 CI artifact、PR 或 Issue 并验证无真实敏感数据进入仓库。
- [ ] 7.3 在真机复测拒绝后授权、设备断开、断网恢复、用户取消、SDK 页面关闭和 Token 过期，验证可恢复路径、单次重试上限与用户状态均符合规格。
- [ ] 7.4 在同一安装上执行退出、会话失效和换号，验证旧初始化/设备/Token 不被复用；若厂商无法提供安全 reset/reinitialize 证据，则保持 P0 未完成并停止发布结论。
- [ ] 7.5 使用 production 配置策略与受控生产环境只读/非破坏验证证明 test 配置被拒绝、production 配置可初始化；缺生产账号、设备或服务端证据时明确保持 `UNVERIFIED`，不得仅凭 Acceptance 关闭 P0。

## 8. 文档同步与最终交付

- [ ] 8.1 按最终 AAR/服务端契约同步 `docs/integrations/qlz-sdk.md`、技术栈、产品概览、CI 门禁和路线图，条件性更新隐私合规说明，并验证不写入 appKey、账号、设备 ID、本机路径或一次性执行日志。
- [ ] 8.2 复核 `git diff` 只包含 QLZ P0 必需实现、测试、门禁与当前真相文档，验证未混入腾讯人脸、Jetifier、protobuf 独立迁移、额外 Manifest 权限最小化、WebView 或销售 UI 重构。
- [ ] 8.3 运行本地 Markdown 链接检查、`git diff --check`、tracked secret/keystore 扫描和 `git ls-files build`，验证没有提交构建报告、厂商临时材料、凭据或本机证据。
- [ ] 8.4 运行 `openspec validate replace-qlz-sdk-and-remove-temporary-config --strict` 与 `openspec validate --all --strict --no-interactive`，确认全部任务和 QLZ/Face 分离验收证据满足后再提交归档评审。
