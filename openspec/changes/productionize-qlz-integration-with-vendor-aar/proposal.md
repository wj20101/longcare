## Why

QLZ 设备评估是正式业务必需能力，当前厂商 AAR 也是完成该能力的必要运行依赖；项目无法修改或替代厂商二进制内部实现。现有方案把 AAR 本身及其内部静态告警作为 production 阻断项，会导致正式环境无法交付，因此需要把责任边界调整为“厂商风险如实登记但不阻断业务，项目只对自身可控配置、接入逻辑和业务回归负责”。

## What Changes

- 正式 APK/AAB MUST 保留并使用当前厂商提供的 QLZ AAR；生产检查从“发现该 AAR 即失败”改为“缺少必需 AAR 或接入不可用才失败”。
- 将 AAR 内部 TLS、遥测及其他闭源实现风险登记为厂商所有的已知外部风险，记录版本、校验和、影响与复核触发条件，但不通过项目代码修改 AAR、植入网络 Hook、伪造设备标识或因静态告警阻断正式业务。
- 只整改项目可控配置：移除所有变体共享的固定测试配置，让 production 明确取得正式 SDK key 并强制关闭测试模式；Debug/显式 Acceptance 与 production 配置不得混用，配置值不得进入版本库、日志或诊断输出。
- 保持 `QlzSdkClient` 为唯一厂商接入边界，维持初始化、设备 ID、BLE 权限、一次性 Token、检测事件及报告回查的现有业务顺序；项目不调用未获厂商确认的内部或非公开绕行 API。
- 调整 production config、vendor readiness、Lint waiver 及其负向测试：继续阻断固定测试配置、production 测试模式、缺失 AAR和项目侧回退；不再因当前 QLZ AAR 的厂商内部 finding 阻断。腾讯人脸及其他独立门禁保持原语义。
- 增加构建产物、adapter、销售流程和受控真机回归证据，证明正式包包含 QLZ 能力且项目侧失败能够安全、可恢复地反馈，不以修复厂商内部实现作为完成条件。
- 同步产品、技术栈、QLZ 集成、CI 门禁、路线图和项目体检规格，明确厂商责任、项目责任、风险接受状态与重新评估条件。
- 本 change 取代 `replace-qlz-sdk-and-remove-temporary-config` 的实施方向；不删除其已采集的基线证据，但旧 change MUST 不再用于指导实现。

## Capabilities

### New Capabilities

- `sales/qlz-device-assessment`: 定义正式环境保留厂商 AAR、项目可控初始化配置、销售设备评估业务兼容性和发布验收契约。

### Modified Capabilities

- `engineering/project-health-assessment`: 将已明确接受且只能由厂商处理的闭源 AAR 内部风险与项目可控发布缺陷分开分类，不再把前者自动视为 production fail-closed 条件。

## Impact

- **Android 构建与配置**：`app/build.gradle.kts` 中的 QLZ 依赖、变体配置和 production release guard；正式配置的 CI/本机注入入口及脱敏检查。
- **Android 接入逻辑**：`QlzSdkClient`、销售设备 gateway/controller、相关 ViewModel 与错误映射；厂商类型继续限制在 app-owned adapter。
- **质量门禁**：QLZ vendor readiness、Lint waiver、release fixture、构建产物检查和质量门禁注册信息；腾讯人脸等其他厂商门禁不受影响。
- **业务验证**：客户选择、设备初始化、BLE 授权/连接、Token、取消/完成、异常恢复和报告回查的自动化与受控真机矩阵。
- **外部依赖与风险**：当前 QLZ AAR 的内部实现由厂商负责；项目记录但不修补其 TLS/遥测行为。厂商提供新包、官方处置说明或现有行为影响业务时重新评估。
- **兼容性**：不改变 route、SavedStateHandle key、Room schema、销售后端接口、Token 字段或用户主流程；正式环境仍依赖可用的 QLZ 正式 key、销售账号、BLE 设备和厂商服务。
