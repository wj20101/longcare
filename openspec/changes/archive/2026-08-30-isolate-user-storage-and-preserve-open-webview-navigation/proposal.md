## Why

当前登录会话把复合身份、展示资料、实名信息和 Token 混在同一份 `User` 中，以明文持久化并通过进程级会话暴露给 UI、Feature 和网络层；Room 订单、内存缓存、系统配置、COS 凭据、待恢复提醒、Alarm/WorkManager 和部分文件记录又没有统一用户归属，退出、Token 失效、换号或重启后可能泄露敏感会话信息或把上一用户的数据和任务交给下一用户。与此同时，应用内 WebView 不应按业务域名做 host 白名单，否则服务端页面跳转或更换域名会造成页面无法打开。

## What Changes

- 建立收敛的用户会话模型：登录返回对象只作为一次性边界 DTO；进程级 `SessionState` 仅公开一个包含 `UserScopeKey`、姓名、头像、角色和性别但不含 Token/身份证号的不可变 `CurrentUser`。完整持久会话只作为数据层内部记录存在，不为每组普通字段新增领域模型或 Provider。
- 以 `companyId + accountId + userId` 形成稳定的 `UserScopeKey`，再由版本化规范输入生成不暴露原始用户标识的命名空间摘要，明确设备级、用户级和会话级数据边界；Token、角色或资料变化不得改变用户存储归属。
- 每个 `UserScopeKey` 使用独立物理命名空间：专属 Room 数据库文件、用户级 DataStore 文件和受管业务文件目录；命名空间保存并校验归属元数据，不同用户即使拥有相同 `orderId` 也不会进入同一存储文件。
- 所有用户业务数据访问都必须由当前会话取得命名空间租约；内存缓存、持久提醒和后台任务不得只使用 `orderId`、裸 `userId` 或当前全局单例推断归属。
- 使用一个 Android Keystore 保护的应用私有加密会话记录，原子保存格式版本、`PENDING/ACTIVE` 阶段、`sessionEpoch`、`CurrentUser`、Token 和当前用户身份证号；不再额外维护公开引导文件与秘密文件之间的一致性。UI 和普通 Feature 只能观察安全的 `CurrentUser`，网络层只取得请求期认证快照，实名认证由既有业务 Repository 在内部使用身份证号；日志、缓存键、后台任务输入和文件名不得接触 Token 或身份证号。
- 为登录、手动退出、Token 失效、账号切换和进程恢复建立同一套会话/存储生命周期编排：先把单一加密会话记录写为 `PENDING`，撤销旧命名空间访问、关闭旧 Room、停止旧任务和 DataStore 消费者、清理旧会话缓存，验证新用户命名空间后再原子切为 `ACTIVE`；同一物理 DataStore 文件在进程内始终只有一个受管实例。只有真实业务需要时才提供窄范围的资料或 Token 更新方法，不保留通用 `updateUser`。
- **BREAKING（本地状态整体冷切换）**：升级时删除旧全局 `app_user` 明文会话以及所有已知 legacy 偏好、DataStore、数据库、业务文件、缓存、提醒和任务恢复状态，不解码、不迁移、不重加密也不回退任何旧值。旧隐私同意状态、旧设备实例 ID、上次登录手机号、兼容性引导标记和更新状态同样不保留；升级后重新展示隐私协议，在用户重新同意后按新版本规则生成安装级设备标识，并要求用户重新登录。
- **BREAKING（服务端重新水合）**：弃用固定名称的全局 Room 业务数据库及无完整用户归属的旧存储。登录成功且当前用户命名空间 Ready 后，系统从服务端重新拉取系统业务配置、订单及其他可恢复业务数据并保存到该用户命名空间；拉取失败时进入明确的空态或重试状态，禁止回退到旧配置、旧缓存、其他用户数据或全局存储。之后每个用户数据库继续遵循已确认的破坏性重建策略，服务端始终是业务事实来源。
- 将待处理服务提醒从无用户维度的 SharedPreferences 数据提升为可验证的用户归属记录；Alarm、通知、PendingIntent 和 WorkManager 的唯一标识及输入数据包含用户作用域。
- 让系统配置、订单/COS 等进程内缓存和用户文件遵循同一会话切换失效规则。新版本仍将隐私同意、安装级设备标识、兼容性引导和应用更新等运行状态按设备级管理，但该分类不构成 legacy 兼容承诺：切换版本时旧值全部重置，后续只按新规则重新产生；上次手机号不得被误当作当前用户作用域或认证依据。
- 固化 WebView 导航契约：初始加载、点击和重定向不按 host 限制，任意 HTTP(S) host 均交给 WebView；继续拒绝 `file:`、`content:`、`javascript:`、`intent:` 和未知自定义协议，保持文件访问关闭、SSL 错误不绕过、混合内容不放宽。
- 不把 Android 全局 `cleartextTrafficPermitted` 改为无条件开启；任意 HTTPS host 可直接加载，HTTP 仍受平台网络安全配置约束。若未来业务明确要求任意明文 HTTP host，必须作为单独安全变更评审，因为该开关不能只限定在 WebView。
- 不修改厂商 AAR、厂商内部实现、生产签名、网络 API 契约、类型安全路由或用户可见业务流程；不引入全库静态加密或需要用户交互解锁的密钥策略。

## Capabilities

### New Capabilities

- `user-scoped-storage`: 定义收敛的安全用户会话、用户归属键、会话秘密保护、数据分类、Room/缓存/文件/后台任务隔离以及登录、退出、失效、换号和重启生命周期。
- `webview-open-host-navigation`: 定义 WebView 对任意 HTTP(S) host 的无白名单导航行为，以及协议、文件访问、SSL 和混合内容的安全边界。

### Modified Capabilities

无。当前仓库没有对应的主规格，本 change 以两个新 capability 描述增量行为。

## Impact

- **模型与领域契约**：`:core:model` 的 `UserScopeKey` 和安全 `CurrentUser`，`:core:domain` 的精简 `SessionState`、用户存储生命周期和持久提醒契约；完整 `User`、Token 和身份证号不再作为跨层可观察模型，也不为每个字段建立独立领域接口。
- **数据与网络层**：`:core:data` 的单一 Android Keystore/AES-GCM 加密会话记录、内部网络认证快照、用户存储注册表、动态 Room/DAO provider、用户 DataStore、schema JSON、系统配置、订单/COS/腾讯凭据等缓存和 Hilt 绑定；不再把完整用户、固定全局数据库及其 DAO 作为跨会话单例注入。
- **Feature 与业务壳层**：登录/UI 只消费安全 `CurrentUser` 或页面 `UiState`，实名认证由既有业务 Repository 在内部组装所需实名数据；`:app` 负责服务提醒、倒计时、AlarmManager、WorkManager、开机恢复、受管图片路径、登录/退出/失效编排以及两处 WebView。
- **兼容性**：不提供任何旧本地状态兼容或恢复路径。升级后重新确认隐私协议、重新生成安装级设备标识并重新登录；旧会话、设备偏好、全局业务缓存和任务恢复状态全部删除。新登录建立独立命名空间并从服务端重新水合，旧用户任务不得在新用户会话中恢复。
- **验证**：增加零 legacy 保留清单测试，验证旧会话、隐私状态、设备实例 ID、登录偏好、兼容性/更新状态、全局数据库、业务偏好、缓存、文件和任务恢复状态均被删除；同时覆盖重新同意隐私、重新生成设备标识、登录后服务端重新水合、秘密边界架构门禁、密钥失效、Room schema/DAO 隔离、会话切换、Worker/Receiver 归属、文件路径和 WebView URL policy，并执行 `preflight_local.sh --full`、Lint、Debug 构建及相关 instrumentation/smoke。
- **外部依赖**：使用 Android 平台 Keystore/JCA，不新增服务端、第三方加密库或厂商依赖；厂商 AAR 保持现状。
