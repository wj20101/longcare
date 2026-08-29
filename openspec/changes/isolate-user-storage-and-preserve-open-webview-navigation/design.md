## Context

变更动机见 [proposal.md](./proposal.md#why)。当前 `DatabaseModule` 在 `SingletonComponent` 中固定打开 `longcare_database` 并直接提供全局 DAO；`DefaultUserSessionRepository` 把整份当前 `User` 存在全局 `app_prefs`；待服务提醒、通知去重、倒计时和系统配置使用无复合用户归属的 SharedPreferences；`IdentificationFaceDataSource` 只按裸 `userId` 动态创建 DataStore；订单、凭据等内存缓存及 Alarm/WorkManager 标识也没有统一的用户边界。`LocationTrackingManager` 已用 `companyId + accountId + userId` 判断换号并停止旧定位，可作为生命周期行为参考，但其 feature 内部类型不能成为全局存储契约。

两处 WebView 当前只检查 HTTP(S) scheme，没有业务 host 比较；实际 host 差异主要来自 `network_security_config.xml` 仅为既有域名开放明文 HTTP。取消 WebView host 白名单与无条件开放应用级明文流量是两件不同的事。

本设计遵循以下已核对的 Android 官方约束：

- [DataStore 官方指南](https://developer.android.com/topic/libraries/architecture/datastore)要求同一进程对同一文件只创建一个 DataStore 实例；DataStore 适合小型配置，复杂和有关联的数据继续使用 Room。
- [Room 官方指南](https://developer.android.com/training/data-storage/room)支持由应用为不同文件名创建数据库；本项目的订单数据仍由 Room 管理。
- [WebView 官方指南](https://developer.android.com/develop/ui/views/layout/webapps/webview)规定允许 WebView 自身处理的 URL 应从 `shouldOverrideUrlLoading` 返回 `false`，不应在该回调内再次调用 `loadUrl`。
- [WebView 文件访问风险指南](https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion)要求不可信网页保持文件、内容和 file-URL 跨域访问关闭；开放 HTTPS host 不改变这些设置。
- [网络安全配置指南](https://developer.android.com/privacy-and-security/security-config)中的明文策略是应用级安全边界，不能只为某一个 WebView 无限制放开。
- [用户数据标识指南](https://developer.android.com/training/articles/user-data-ids)建议非广告场景优先使用应用私有且可重置的 GUID/FID，而不是不可重置硬件标识；新版本设备实例 ID 因此采用用户同意隐私后生成的应用内 GUID。
- [隐私检查清单](https://developer.android.com/privacy-and-security/about)要求在明确同意前限制设备标识访问并尽量减少保留数据；冷切换会重置旧同意状态和旧设备标识。

全部 legacy 本地状态采用用户已明确确认的整体冷切换策略。该决策是本 change 对仓库通用“保持持久化兼容、优先显式迁移”约束的有意例外：旧会话、隐私同意、设备标识、设备偏好、全局业务缓存、提醒和文件均不读取或迁移；新版本写入后的正常生命周期仍遵循本设计的设备级、用户级和会话级边界。新建的每用户数据库继续保留 `fallbackToDestructiveMigration(dropAllTables = true)`。厂商 AAR、服务端契约和生产网络配置保持不变。

## Goals / Non-Goals

**Goals:**

- 让完整复合用户身份一一对应独立 Room 文件、用户 DataStore 文件和业务文件目录，并在打开时验证归属。
- 让 Repository、后台任务和平台回调只能通过当前有效存储代次访问用户数据；换号期间旧操作无法落入新用户空间。
- 建立新版本的设备级设置和单一加密会话引导能力，同时在正常退出或换号时清理会话秘密、临时文件和旧任务。
- 在任何恢复或用户存储打开前一次性淘汰全部 legacy 本地状态，重新授权和登录后由服务端填充当前用户配置及业务缓存。
- 让两处 WebView 对所有合法 HTTP(S) host 使用同一 URL policy，同时保持协议、文件、TLS、混合内容及应用级明文安全边界。

**Non-Goals:**

- 不把“分文件”宣称为静态加密。文件仍依赖 Android 应用沙箱；若要抵御 root、离线取证或已解锁设备上的本地攻击，需要另行设计 Keystore/数据库加密和密钥恢复策略。
- 不允许多用户同时处于活动状态，也不在未登录时自动打开最近用户的数据。
- 不抢救、推断、解码、转换或合并任何旧会话、设备偏好、全局数据库、旧提醒和共享文件记录。
- 不无条件开放任意明文 HTTP，不添加 JavaScript bridge，不修改厂商 AAR 或厂商内部逻辑。
- 不改变服务器作为订单事实来源的定位，也不改变现有网络 API、route contract 或用户业务流程。

## Decisions

### 1. 复合身份生成版本化不透明命名空间

在 `:core:model` 新增 Android-free 的 `UserScopeKey(companyId, accountId, userId)`。它使用固定顺序、长度明确的 UTF-8 v1 规范编码，并以完整 SHA-256 小写十六进制值生成 `NamespaceId`。路径和日志只使用 `v1_<digest>`，不直接包含公司、账号、用户 ID 或 Token。摘要只是稳定、不暴露路径 PII 的索引，不是认证凭据；每次打开仍要验证内部归属元数据。

不采用以下替代方案：

- **裸 `userId`**：不同公司或账号体系可能重复，当前代码已经存在该风险。
- **把原始复合 ID 拼进文件名**：虽可唯一，但在备份、日志和文件诊断中暴露用户标识。
- **随机 UUID 映射**：需要另一个全局映射表才能在重装以外的进程恢复中稳定找到文件，映射损坏会造成孤儿存储。

### 2. 全局引导存储与每用户物理存储明确分层

新版本使用一个独立于旧 `app_prefs` 的全局会话文件作为唯一会话事实来源。该文件位于 `noBackupFilesDir`，通过进程级唯一 DataStore 实例原子读写整体密文；明文 `SessionEnvelope` 只在数据层短暂存在，包含格式版本、`PENDING/ACTIVE` 阶段、持久 `sessionEpoch`、安全 `CurrentUser`、Token 和当前用户身份证号。整条记录使用 Android Keystore 中不可导出的应用密钥执行 AES-GCM 加密，阶段与载荷不得拆成可独立提交的文件。UI、普通 Feature 和任务输入只能取得不含 Token/身份证号的 `CurrentUser` 或稳定作用域值；旧 `app_user` 永远不作为该文件的输入。

隐私同意、安装级设备标识、兼容性引导、上次登录手机号及更新状态在新版本正常运行期间仍位于各自设备级存储，但不复制任何 legacy 值。冷切换完成后隐私状态默认为未同意、设备标识不存在、上次手机号为空，其余设备运行状态按系统现状重新计算。用户重新同意隐私后使用 `UUID.randomUUID()` 生成新的应用私有 GUID 并存入内部设备级存储；不再以旧持久值或硬件标识恢复设备身份。全局文件不得保存订单、业务配置、提醒列表、图片记录或多个用户的历史业务数据。

每个用户的 v1 命名空间采用以下物理布局：

```text
databases/longcare_user_v1_<sha256>.db
files/datastore/user_v1_<sha256>.preferences_pb
files/user_scopes/v1/<sha256>/persistent/<purpose>/...
cache/user_scopes/v1/<sha256>/session/<purpose>/...
```

- Room 保存订单、老人信息、本地状态、项目、图片记录以及待服务提醒等有关联或需要事务的数据。
- 用户 DataStore 保存小型用户业务配置和状态；会话 Token 与短期 COS/腾讯凭据不写入这里。
- `persistent` 保存已被 Room 记录引用的用户业务文件；`session` 保存人脸、拍摄中间文件和上传临时文件，退出或换号时删除。
- 命名空间描述文件、Room 单行 metadata 表和用户 DataStore metadata key 都记录格式版本及完整 `UserScopeKey`。注册表逐项核对；任何不一致均拒绝打开。Room 内普通业务表不重复增加 owner 列，因为物理文件已经是分区边界，重复 owner 容易漏写或形成互相矛盾的数据；需要离开进程的任务仍显式携带作用域。
- Room 仅保存相对路径或受验证的文件句柄；文件解析先规范化并确认仍位于当前用户根目录，禁止绝对路径和 `..` 逃逸。

相比“一个全局数据库、每行增加 user columns”，独立文件能从物理上避免漏加 WHERE 条件造成串号，也让单用户清理和重建不会影响其他用户；代价是需要动态数据库生命周期和更多磁盘文件，本设计用活动单用户句柄控制资源。

### 3. 通过注册表、状态机和存储租约管理实例

在 `:core:domain` 定义当前作用域、存储状态和生命周期契约，在 `:core:data` 实现进程级 `UserStorageRegistry`。状态至少包含 `LoggedOut`、`Opening`、`Ready(scope, epoch, generation)`、`Closing` 和 `Failed`；所有转换由 `Mutex` 串行化。

注册表维护单调递增的进程 `generation`，每次登录、退出或换号都先使旧代次失效。内部 `UserStorageLease` 同时包含 scope、sessionEpoch 和 generation；每次事务提交、文件落盘及异步回调完成前都再次校验。业务层不直接接触 Room 类型，Repository 通过 `UserDatabaseAccess.withCurrentLease { ... }` 取得当前 DAO。用户作用域流切换时使用 `flatMapLatest` 或等价取消语义终止旧观察流。

实例规则：

- 同一时刻只保持当前用户的 Room 句柄为打开状态；切换时停止新请求、等待或取消已登记操作，再调用 `close()`。
- DataStore 没有与 Room 等价的业务级 `close()` 契约。注册表按规范化文件路径缓存唯一实例，进程生命周期内相同路径永不重复构造；离开作用域时取消消费者并撤销租约，实例变为不可访问的 idle 状态，同一用户重新登录时复用它。
- Hilt 只提供注册表、协调器和 scoped access facade，不再提供全局 `LongCareDatabase` 或 DAO。这样 Worker、Receiver、Repository 都必须经过同一访问门。

不采用在每个 Repository 中用动态文件名调用 `Room.databaseBuilder`/`preferencesDataStore` 的方式，因为并发时会产生重复实例、关闭竞态和 DataStore 的单文件实例违规。也不采用长期同时打开所有用户数据库，因为应用只允许一个活动登录用户。

### 4. 会话和存储用一个可恢复的切换编排

登录/换号顺序：

1. 网络登录返回完整 `User` 后生成 scope、namespace 和新的 `sessionEpoch`，将引导状态原子写为 `PENDING`；该阶段不向业务暴露 `LoggedIn`。
2. 若存在旧会话，先把旧 generation 失效，停止定位、倒计时、通知、上传及旧后台任务，取消旧观察流，清空 session 缓存/临时文件并关闭旧 Room。
3. 创建或打开目标命名空间，核对描述文件、Room metadata 和 DataStore metadata；不存在时在同一 scope 下初始化。
4. 所有存储就绪后把引导状态原子切为 `ACTIVE`，再发布 `SessionState.LoggedIn` 和 `UserStorageState.Ready`。
5. 触发受当前 lease 约束的登录后重新水合：强制从服务端获取系统业务配置、初始订单及其他可恢复数据并写入当前用户命名空间。会话激活不依赖所有业务数据一次性下载完成；对应功能在获取期间显示加载态，失败时显示空态或可重试错误，绝不读取 legacy、全局或其他用户值。

退出/Token 失效顺序：

1. 先撤销当前 generation 并停止发布活动登录态，使新调用立即 fail closed。
2. 无论远端 logout 成败，取消 scope+epoch 对应的 WorkManager/Alarm/PendingIntent、提醒记录和实时能力，清除 Token、临时凭据、内存缓存及 session 文件。
3. 关闭 Room、移除引导会话并发布 `LoggedOut`。持久用户数据库、用户 DataStore 和 `persistent` 文件保持关闭并保留，只有相同复合身份未来重新登录才能再次打开。

进程启动只有在冷切换标记已经提交后才允许读取新格式会话文件。若看到新格式 `ACTIVE` envelope，会核对并恢复同一 scope/epoch；若看到 `PENDING`，会先完成或安全回滚打开过程，不把它直接当成已登录。旧 `app_user` 即使完整可解码也直接删除；新密文解密失败、字段缺失、metadata 不一致或 namespace 损坏均进入登出/可重建状态，绝不扫描最近用户文件兜底。

此状态机避免“先写登录态、后切数据库”产生短窗口。只依赖内存锁而不持久化阶段的替代方案无法覆盖打开过程中进程被杀的情况，因此不采用。

### 5. 后台任务使用作用域、会话代次和业务 ID 三元身份

定义 `UserTaskIdentity(namespaceId, sessionEpoch, businessId)`：

- WorkManager unique work name、tag 和 input data 全部包含 namespace、epoch、orderId/任务类型。
- Alarm/PendingIntent 使用显式组件，并以包含 namespace、epoch、业务 ID 的唯一 data URI 参与 Android PendingIntent 身份；不能只依赖 extras，因为 extras 不参与 PendingIntent 相等性。
- 通知 ID、去重记录和倒计时记录以相同身份派生；持久提醒移入当前用户 Room。
- Worker、Alarm Receiver、通知 Receiver 和 Boot Receiver 在产生任何通知、数据库或文件副作用前，先等待会话引导解析，并精确比较当前 namespace+epoch+generation。不匹配即取消/删除自身记录。
- Boot Receiver 只恢复当前 `ACTIVE` 会话命名空间的提醒，不枚举其他用户数据库。

退出会清除当前 epoch 的提醒和系统调度，因此同一用户重新登录也不会恢复上次已退出会话的闹钟。用户持久业务缓存仍可复用，两者边界明确。

### 6. 缓存和文件按统一分类迁移

- 订单内存缓存键改为 `generation + namespace + orderId + planId`，或改为每代独立 cache 并在关闭时整体清空。
- `SystemConfigManager` 的持久业务配置进入用户 DataStore，内存值绑定 generation。
- COS 临时凭据、腾讯临时凭据和其他授权缓存只留在 session 内存，以 scope+epoch 作为键并在切换时清空；不再只按 folderType/App ID 缓存。
- `IdentificationFaceDataSource` 不再自行创建 `user_${userId}_prefs`，改用注册表提供的当前用户 DataStore 和 session 文件根。
- 图片捕获、待上传和已记录文件统一从 `UserManagedFiles` 获取目录；清理必须指定 lease 与 purpose，禁止删除共享顶层目录。

### 7. 全部 legacy 本地状态进行幂等的一次性冷切换

应用启动在初始化会话 Repository、网络身份、用户存储注册表和业务后台恢复之前执行 `user_storage_namespace_cutover_v1`。切换标记位于新版本专用设备文件，旧版本从未写入；只有标记不存在时执行以下顺序：

1. 阻断会话和用户业务入口，取消已知旧 WorkManager、Alarm、PendingIntent 和活动通知，停止旧服务及进程内缓存使用，确保清理期间没有旧状态重新写回。
2. 删除旧 `app_prefs/app_user`、`privacy_consent`、`device_instance_id_store`、`login_preferences`、`device_compatibility_prefs`、`system_config_prefs`、`pending_orders_storage`、通知去重、倒计时、应用更新恢复状态及其他列入清单的 legacy SharedPreferences/DataStore；不读取或反序列化其中载荷。
3. 调用 `Context.deleteDatabase(LongCareDatabase.DATABASE_NAME)` 删除 `longcare_database` 及 Room sidecar，删除 `user_<bareUserId>_prefs`、旧共享业务/临时目录、旧缓存和可识别的遗留下载文件。删除目标使用显式清单和受验证的应用私有路径，不对整个应用根目录执行递归清空。
4. 每个删除和取消动作都设计为可重复；任一步骤失败或进程中止时不写 marker，下次启动从头安全重试。只有全部步骤成功后才原子提交新版本 cutover marker。
5. marker 提交后发布未同意隐私、未登录、无设备标识且无活动用户命名空间的冷启动状态。用户重新同意隐私后初始化新 GUID，重新登录后才创建加密会话和目标用户命名空间。
6. 新会话 Ready 后执行登录后重新水合并只写当前 lease；以后某一个用户数据库 schema 不兼容时，仅该文件使用已确认的 `fallbackToDestructiveMigration(dropAllTables = true)`，不触碰其他新版本用户命名空间。

不提供任何 legacy 本地状态迁移或兼容读取。代码回滚到旧版本也只能看到空状态，用户需要重新同意隐私并登录，旧版随后按自身逻辑从服务端重建；已经删除的会话、设备状态、缓存和文件不可恢复，这是用户确认的有意取舍。

### 8. WebView 共享开放 host、限制能力的 URL policy

在跨两处入口可复用的 Android 层定义单一 `WebUrlPolicy`：只有 URI 可解析、scheme 为不区分大小写的 HTTP/HTTPS 且 host 非空时允许。初始 URL 先验证；后续 `shouldOverrideUrlLoading` 对允许 URL 返回 `false`，对 file/content/javascript/intent/data/未知 scheme 或畸形 URL 返回 `true`，不发隐式 Intent，也不在回调内再次 `loadUrl`。

两处 WebView 保持 `allowFileAccess=false`、`allowContentAccess=false`、file URL 跨域关闭、混合内容拒绝、SSL 错误 cancel 和 Safe Browsing；隐私页 JavaScript 继续关闭，业务页只保留已有必要 JavaScript 且不添加原生 bridge。主文档错误统一转成可重试状态。

不新增 host allowlist，也不要求重定向与首个 host 相同。`network_security_config.xml` 的应用级明文策略保持现状：任意 HTTPS host 可打开；HTTP URL 虽不被 WebView policy 按 host 拦截，平台仍可能拒绝未配置 host，此时显示失败而不是扩大全局明文权限。

### 9. 模块边界和落点

- `:core:model`：`UserScopeKey`、`NamespaceId` 等 Android-free 值对象。
- `:core:domain`：当前 scope、session epoch、存储状态/生命周期、scoped repository 与持久提醒契约，不暴露 Room、Context 或文件 API。
- `:core:data`：namespace codec、Room/DataStore registry、metadata、动态 DAO access、cutover、session repository、用户配置和缓存实现。
- `:feature:identification` 及相关 feature：通过领域/数据抽象取得用户 DataStore 或文件句柄，不自行拼路径或创建存储实例。
- `:app`：冷切换启动门、登录/退出/Token 失效协调器、登录后重新水合、Alarm/WorkManager/Receiver/通知、受管图片平台实现及两处 WebView 组装。现有 route-bound legacy 代码只做必要重构和删除，不提供兼容读取，也不扩大 allowlist。

## Risks / Trade-offs

- **[Risk] 加密会话记录可能让用户误以为所有用户文件都已加密** → 文档明确只有当前会话秘密使用 Keystore/AES-GCM；每用户 Room、DataStore 和业务文件的分区是账号逻辑隔离，仍主要依赖 Android 应用沙箱，数据库/文件全量静态加密另立安全变更。
- **[Risk] 动态 DAO access 改动面较大，遗漏一个全局 DAO 即可绕过隔离** → 删除 Hilt 全局 DAO provider，增加架构门禁扫描 `Room.databaseBuilder`、`preferencesDataStore` 和直接 DAO 注入的允许位置。
- **[Risk] 换号与旧协程、Worker、Alarm 回调竞态** → 每次提交前验证 scope+epoch+generation，状态机先撤销旧租约，再开放新命名空间；使用确定性并发测试覆盖延迟回调。
- **[Risk] DataStore registry 会为本进程曾使用的每个账号保留一个小型 idle 实例** → 只在真正登录时懒创建，不预扫描目录；应用进程结束后自然释放。优先保证官方单文件单实例约束，不做不安全的关闭重建。
- **[Risk] 多用户文件增加磁盘占用** → 同时只打开一个 Room；提供按 scope 的过期缓存维护入口，任何清理都先验证 metadata，绝不跨目录批量删除。
- **[Risk] 冷切换会删除旧会话、隐私同意、设备标识、设备设置、缓存、提醒和文件** → 这是用户确认的零兼容策略；应用明确重新授权/登录，设备标识重新初始化，服务端配置和业务数据只写入新命名空间，cutover 通过显式清单和幂等 marker 防止半清理状态。
- **[Risk] 登录后服务端重新水合失败会暂时没有配置或业务数据** → 会话仍保持明确身份，受影响功能展示加载/空态/可重试错误；禁止使用任何旧值作为可用性兜底。
- **[Risk] 开放任意 HTTPS host 扩大不可信网页来源** → 不提供 JS bridge，关闭本地文件/内容能力、混合内容和 SSL 绕过；只放开 host，不放开高风险能力。
- **[Risk] 任意 HTTP host 仍可能被平台拦截** → 页面显示明确错误和重试；若业务未来必须支持任意明文站点，单独评审应用级网络安全风险。

## Migration Plan

1. 先增加纯模型、namespace codec、metadata、状态机、独立加密会话文件和 registry 的测试及实现，不接入业务；加入禁止新全局用户存储实例和秘密跨层暴露的架构门禁。
2. 实现带显式删除清单、取消钩子和独立 marker 的幂等冷切换器，但在新存储链路完成前不启用生产入口。
3. 将 Room 构建和 DAO 调用改为当前 lease 动态访问，增加 reminder 表与 schema 快照；保持每用户数据库的破坏性 fallback。
4. 按新版本边界重建设备级/用户级/session 级存储，改造系统配置、Identification DataStore、缓存和受管文件目录，不编写 legacy 复制逻辑。
5. 接入登录、手动退出、Token 失效、进程恢复、换号协调器和登录后重新水合，再改造 Worker、Alarm、PendingIntent、通知及 Boot 恢复身份。
6. 启用冷切换启动门，通过升级安装、故障注入、重新授权/登录、服务端拉取失败和双用户测试确认零 legacy 回退且不会串号。
7. 提取共享 Web URL policy，接入两处 WebView，保持 network security config 和 WebView 安全设置不放宽。
8. 执行 focused unit/instrumentation tests、`bash scripts/quality/preflight_local.sh --full`、`:app:lintDebug`、`:app:assembleDebug`，并用 android CLI/adb 在模拟器验证旧版升级→重新授权/登录 A→重新水合→退出→登录 B→重启→通知/WebView 的完整路径。

回滚代码不会恢复任何已删除的 legacy 状态；旧版本只能以冷启动重新授权、登录并从服务端拉取。若新版本在 cutover 中失败，marker 未提交且下次启动会幂等重试；若某一新用户命名空间损坏，只允许重建该用户数据库，不能删除其他用户目录。

## Verification Strategy

- **模型/路径单测**：复合字段任一变化产生不同 namespace；Token 变化不改变 namespace；摘要稳定且路径不含原始 ID；路径逃逸被拒绝。
- **Registry 并发单测**：同文件只创建一个 DataStore、同用户并发只打开一个 Room、旧 generation 延迟写被拒绝、切换流停止、同用户重登录复用命名空间。
- **Room instrumentation**：A/B 两个独立数据库写入相同 orderId 后互不覆盖；metadata 不匹配 fail closed；单用户 schema 不兼容只重建自身；schema JSON 与当前版本一致。
- **Cutover instrumentation**：预置旧 `app_user`、隐私同意、设备 ID、登录/兼容性/更新状态、数据库、legacy prefs/DataStore、任务和共享目录，升级后全部删除且处于未授权/未登录/无 namespace；逐步注入失败验证 marker 不提前提交、重试幂等且不误删新版本文件。
- **重新水合测试**：重新同意隐私后只生成新 GUID；新登录 Ready 后从服务端写入当前 namespace；配置或业务拉取失败只进入空态/重试，绝不回退 legacy、全局或其他用户值。
- **后台任务测试**：unique work、PendingIntent data、通知 ID 包含 namespace+epoch+业务 ID；A 的任务在 B 登录、登出、重启和延迟回调时无副作用。
- **文件与缓存测试**：A/B 同订单同用途文件路径不同；清理 A 不影响 B；系统配置、COS/腾讯凭据和订单缓存换号后不命中。
- **WebView 测试**：任意 HTTPS host、跨域跳转和端口通过；危险/无 host URL 阻断；两入口语义一致；SSL/DNS/明文平台拒绝进入失败与重试；设置快照确认无文件访问、bridge 或混合内容放宽。
