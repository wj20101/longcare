## 1. 固定责任边界与可复用基线

- [x] 1.1 保存当前 QLZ AAR 的文件名、SHA-256、dependencyInsight、Lint、Manifest、consumer rules 和现有 production guard 基线，并验证证据能够单独定位 QLZ 而不混入腾讯人脸结果。
- [x] 1.2 使用 Android CLI 官方资料复核当前蓝牙权限、Network Security Configuration 和安全网络建议，并确认项目不以修改厂商 AAR 内部实现作为实施任务。
- [x] 1.3 扫描应用源码中的 QLZ 厂商 import 和调用点，固定唯一 app-owned adapter 边界，并用自动化断言验证 Feature/Core 没有直接新增厂商类型依赖。
- [x] 1.4 在现有 QLZ 集成/路线图文档中登记“当前 AAR 必需、厂商内部风险已接受且非项目阻断、出现新包/厂商说明/监管或业务影响时复核”，并验证未把风险写成已修复。

## 2. 保留并校验正式环境 AAR

- [x] 2.1 将当前批准的 QLZ AAR 文件名和 SHA-256 建立为构建校验输入，验证原文件字节未被修改、重新打包或重命名替代。
- [x] 2.2 保留 `implementation(files(...qlzsdk...aar))` 依赖并删除“当前 AAR 存在即不安全”的 Gradle 判定，使用 dependencyInsight 验证 Debug/Release 都解析到唯一批准文件。
- [x] 2.3 增加 production AAR 缺失、摘要变化和正常存在的负向/正向 fixture，验证缺失或未知二进制失败、当前批准 AAR 通过。
- [x] 2.4 增加 APK/AAB 内容检查，验证 minified 产物仍包含销售评估所需 QLZ 入口类且未打包第二份 QLZ 实现。

## 3. 生产化项目可控配置

- [x] 3.1 用统一 Gradle provider 从 `QLZ_SDK_KEY` Gradle property 或同名 CI environment 读取配置，验证空白值归一为空且诊断不打印实际值。
- [x] 3.2 为 Debug、显式 Acceptance 和 production 建立明确模式策略：production 强制测试模式 false，Acceptance 必须显式声明，Debug 未配置时仍可编译；用配置矩阵测试验证三种用途不可混淆。
- [x] 3.3 删除源码中的固定测试 key 和所有变体共享的固定测试模式，把解析后的值写入相应 BuildConfig 字段，并用 tracked-source/生成产物扫描验证仓库不再包含固定值或配置日志。
- [x] 3.4 更新本机与 CI 的脱敏配置说明和示例键名，验证示例没有真实 key、Token、账号、设备 ID 或服务端 secret。

## 4. 保持 QLZ 接入和销售业务语义

- [x] 4.1 让 `QlzSdkClient` 使用显式运行配置并保持“设置模式→构建 CheckConfig→初始化→设备 ID→Token 页面”的现有顺序，用 focused tests 验证空配置、重复初始化和异常返回。
- [x] 4.2 如静态厂商 API 无法直接测试，在 QLZ integration 包内增加窄 facade 原样委托当前公开 API，并用 fake 验证参数、调用顺序和事件映射；确认生产实现没有设备 ID注入、网络 Hook、反射或未公开 API。
- [x] 4.3 扩展 QLZ 事件/错误测试，覆盖进度、完成、取消、页面关闭、空 Token、初始化异常和结果解析失败，并验证用户文案脱敏且不伪造成功。
- [x] 4.4 扩展销售 gateway/ViewModel focused tests，验证取得设备 ID 后才请求原样的 `/V1/Sale/GetCheckToken`、Token 最多刷新一次、取消不算完成且完成后重新查询 LongCare 报告。
- [x] 4.5 覆盖 BLE 权限拒绝后恢复、重复点击和 Activity 页面打开失败，验证不会无限重试、重复打开厂商页面或丢失当前客户上下文。

## 5. 按责任边界调整发布门禁

- [x] 5.1 更新 `verify_production_release_config.sh` 及 Gradle 调用参数，阻断 production 缺 key、已知测试配置、测试模式、AAR 缺失/摘要变化和 production/Acceptance 同时启用，并验证合法 QLZ 输入不再因 AAR存在失败。
- [x] 5.2 为 production config guard 建立 shell fixture 矩阵，逐项验证每个项目可控根因独立失败、未知参数 fail closed、Acceptance 明确通过且输出不泄露配置值。
- [x] 5.3 更新 vendor readiness，把当前 QLZ AAR 的已登记内部 finding 输出为非阻断风险信息而不加入 violation 集合，并用合成 Lint 报告验证腾讯人脸等既有根因仍阻断。
- [x] 5.4 收紧 QLZ `TrustAllX509TrustManager` waiver 到当前 issue、文件名和版本，更新接受原因，并用负向 fixture 验证应用源码、未知 QLZ 文件或其他依赖中的同类新增 finding 仍失败。
- [x] 5.5 同步 quality gate registry、CI workflow 自检和质量快照分类，验证发布入口仍调用 production/vendor guards，且 QLZ 非阻断分类没有移除其他 release-required 检查。

## 6. 自动化与构建产物验证

- [x] 6.1 运行 QLZ adapter、销售 gateway/ViewModel、脚本 fixture 和受影响模块测试，验证 focused tests 全部通过且协程取消仍正确传播。
- [x] 6.2 运行 `bash scripts/quality/preflight_local.sh --full`、`:app:lintDebug`、`:app:assembleDebug` 和 warning allowlist，验证没有新增项目源码 TLS finding、secret、模块边界或常规构建回退。
- [x] 6.3 使用显式 Acceptance 配置构建 minified APK/AAB，验证 acceptance 标识、R8、Manifest、ABI、Baseline Profile 和 QLZ 打包检查通过，且不把该结果描述为 production 证据。
- [ ] 6.4 在正式 QLZ key 通过受控环境提供时执行 production 配置校验和可达的 Release 构建步骤，验证 QLZ 专项根因清零；若正式输入或其他独立 blocker 缺失，明确记录为未验证而不伪造绿色结果。
- [ ] 6.5 解包已生成的 APK/AAB，验证只有批准的 QLZ 实现、production 测试模式关闭且提交证据中不包含 SDK key、Token 或其他敏感运行数据。

## 7. 业务验收与长期事实同步

- [ ] 7.1 使用受控销售账号和 BLE 检测设备完成客户选择、初始化、设备 ID、Token、授权、连接、进度、完成和报告回查，验证正式依赖 AAR 的主链路可用。
- [ ] 7.2 真机覆盖拒权后授权、设备断开、断网/厂商异常、用户取消、页面关闭和 Token 过期，验证项目侧恢复边界符合规格；缺设备或账号时保留未验证状态。
- [x] 7.3 按维护矩阵同步产品概览、系统概览、技术栈、QLZ 集成、CI 门禁和路线图，验证所有文档都说明“当前 AAR 必需且内部风险由厂商负责、项目门禁只覆盖可控逻辑”，并移除“必须替换 AAR才能发布”的过时表述。
- [x] 7.4 复核 `git diff`、Markdown 链接、`git diff --check`、tracked secret/keystore 扫描和 `git ls-files build`，验证没有提交构建报告、真实配置、临时探针或本机路径。
- [x] 7.5 运行 `openspec validate productionize-qlz-integration-with-vendor-aar --strict` 与 `openspec validate --all --strict --no-interactive`，确认新 change 自洽，并在交付说明中明确旧 `replace-qlz-sdk-and-remove-temporary-config` 不再实施、其已完成基线仅作证据复用。
