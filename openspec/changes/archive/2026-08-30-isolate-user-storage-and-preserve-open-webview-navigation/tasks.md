## 1. 用户身份与领域契约

- [x] 1.1 在 `:core:model` 实现 Android-free 的 `UserScopeKey`、`NamespaceId` 和 v1 规范编码/SHA-256 派生，并用单测验证三项身份任一变化都会换 namespace、Token/资料变化不会换 namespace、文件标识稳定且不包含原始 ID
- [x] 1.2 在 `:core:model`/`:core:domain` 定义只含 `UserScopeKey`、姓名、头像、角色和性别的不可变 `CurrentUser`，让公开 `SessionState` 只暴露该视图，并用编译/架构测试验证 Token、身份证号和完整登录响应无法进入 UI、导航、普通 Feature 或任务输入
- [x] 1.3 在 `:core:domain` 定义 `UserStorageState`、scope/sessionEpoch/generation 租约、生命周期错误和当前作用域契约，并用纯 JVM 单测验证 LoggedOut/Opening/Ready/Closing/Failed 的合法转换与过期租约拒绝语义
- [x] 1.4 定义 `UserTaskIdentity` 的领域值对象与确定性编码，验证 namespace、epoch、任务类型或业务 ID 任一变化都会产生不同身份且不把 Token 写入编码结果

## 2. 每用户存储基础设施

- [x] 2.1 在 `:core:data` 实现版本化 `UserNamespacePaths` 和私有目录布局，使用单测验证 Room/DataStore/persistent/session 路径属于同一摘要命名空间、路径无原始 ID 且绝对路径和 `..` 无法逃逸根目录
- [x] 2.2 实现命名空间描述文件及创建/核对逻辑，验证 scope、格式版本、摘要缺失/损坏/不匹配时 fail closed，新命名空间初始化是幂等的
- [x] 2.3 实现按规范化文件路径缓存唯一实例的用户 DataStore registry，并用并发单测验证同文件只创建一个实例、不同用户使用不同文件、离开后不可访问而同用户重登录复用原实例
- [x] 2.4 为 Room 增加单行 namespace metadata 与用户级 pending reminder entity/DAO，更新数据库版本和 schema JSON，并通过 Room schema 导出及 DAO focused tests
- [x] 2.5 实现按 `longcare_user_v1_<digest>.db` 创建数据库的 factory，保留 `fallbackToDestructiveMigration(dropAllTables = true)`，并用 instrumentation 验证 metadata 核对、数据库关闭和单用户破坏性重建不影响另一用户文件
- [x] 2.6 实现 `UserStorageRegistry` 的 Mutex 状态机、generation、活动 Room 句柄、DataStore access 与租约校验，并用确定性并发测试验证并发打开复用、A→B 关闭顺序、旧异步提交拒绝和观察流切换终止
- [x] 2.7 实现 `UserManagedFiles` 的 persistent/session purpose 目录、相对句柄解析与按 lease 清理，并用单测验证清理 A 或某一 purpose 不影响 B、持久文件或其他 purpose

## 3. Room 与 Repository 接入当前命名空间

- [x] 3.1 提供不向 domain 暴露 Room 类型的 `UserDatabaseAccess.withCurrentLease`/观察访问 facade，并用 fake database 测试未登录、Opening、过期 generation 和 Ready 四种访问结果
- [x] 3.2 将 `OrderRoomSyncDelegate` 与 `UnifiedOrderRepository` 的订单/老人/状态/项目读写改为 lease 内动态 DAO 访问，并运行其 focused unit tests 验证同 orderId 在 A/B 数据库互不覆盖且换号后旧 Flow 不再发射
- [x] 3.3 将 `ImageRepository` 的图片记录读写和删除改为当前数据库与当前 `UserManagedFiles`，并用 focused tests 验证数据库记录只引用当前 namespace 的相对文件句柄
- [x] 3.4 移除 Hilt 中固定全局 `LongCareDatabase`/DAO provider，改为只绑定 registry、factory 和 scoped access facade，并通过 `:core:data:compileDebugKotlin` 及 DI 测试确认 Repository/Worker/Receiver 无法直接取得跨会话 DAO
- [x] 3.5 增加双用户 Room instrumentation：A/B 写入相同 orderId、项目、状态和图片记录后分别重开文件，验证查询、更新、删除和 schema metadata 完全隔离

## 4. 会话引导与统一生命周期

- [x] 4.1 在 `:core:data` 新建位于 `noBackupFilesDir`、独立于旧 `app_prefs` 的进程级唯一会话 DataStore，以 Keystore 不可导出密钥和 AES-GCM 原子保存完整 `SessionEnvelope` 密文；用单测验证 PENDING/ACTIVE 与载荷同提交、磁盘不出现 Token/身份证号明文、密钥失效/密文损坏 fail closed，且实现不读取旧 `app_user`
- [x] 4.2 重构 `UserSessionRepository`，让完整登录结果只作为一次性写入 DTO，公开流只返回 `CurrentUser`，网络层只取得请求期认证快照、实名 Repository 只在内部取得身份证号，并移除通用 `updateUser`；用 repository、拦截器和架构测试验证秘密边界
- [x] 4.3 实现登录/换号协调器的 PENDING→撤销旧代次→打开并验证新 namespace→ACTIVE 顺序，并用 fake runtime/storage 测试存储 Ready 前绝不发布 LoggedIn
- [x] 4.4 将手动退出接入统一协调器，确保远端 logout 成功、失败和抛异常都执行本地租约撤销、任务停止、秘密/临时文件清理与 Room 关闭，并通过退出 focused tests
- [x] 4.5 将网络 Token 失效路径接入同一协调器，移除只清 Token 的旁路，并用拦截器/会话测试验证失效后用户数据库不可访问、新版本正常设备级偏好保持不变
- [x] 4.6 实现进程启动只在 cutover marker 已提交后恢复新格式 ACTIVE/PENDING envelope 或安全回滚，在 metadata 不一致时 fail closed，并用 process-recreation/instrumentation 测试验证不读取旧 `app_user`、不扫描且不打开其他用户文件
- [x] 4.7 把定位、倒计时、上传和其他实时能力注册为会话 runtime cleanup hook，复用现有定位换号行为并用生命周期测试验证 A 的能力在 B Ready 前已经停止且 CancellationException 不被吞掉
- [x] 4.8 增加账号切换竞态集成测试，控制 A 的数据库/网络/上传回调在 B Ready 后返回，验证其不写任何 namespace、不更新缓存、不显示通知也不重启服务
- [x] 4.9 实现受当前 lease 约束的登录后重新水合协调器，在新会话 Ready 后强制获取系统业务配置、初始订单及其他可恢复数据并写入当前 namespace；用成功、并发切号和网络失败测试验证加载/空态/重试语义且绝不回退 legacy、全局或其他用户值

## 5. 用户偏好、缓存与受管文件重构

- [x] 5.1 将 `SystemConfigManager` 改为只读写当前用户 DataStore并绑定 generation，不复制旧 `system_config_prefs`；用双用户及重新水合单测验证相同配置 key 不串号、登出或拉取失败不回退旧值且新网络值可持久化
- [x] 5.2 将 `OrderInfoMemoryCache` 的值与加载 Mutex 绑定 namespace+generation，切换时整体失效，并用并发单测验证 A/B 相同 orderId/planId 不复用值或锁
- [x] 5.3 将 `CosConfigCache` 和动态凭据 provider 绑定 scope+sessionEpoch 并在生命周期退出时清空，验证 B 不读取 A 的 folderType 缓存且主线程 fallback 只使用当前 epoch 凭据
- [x] 5.4 将 `TencentCredentialCache` 及同类临时授权缓存绑定 scope+sessionEpoch 并接入 cleanup hook，验证相同 App ID 换号后重新获取而非复用旧缓存
- [x] 5.5 将 `IdentificationFaceDataSource` 从 `user_<bareUserId>_prefs` 和共享 cache 目录迁到 registry DataStore 与当前 session purpose，运行 feature tests 验证复合身份隔离、同文件单实例和退出只删当前临时人脸文件
- [x] 5.6 将相机捕获、上传中间文件及持久图片入口统一改用 `UserManagedFiles`，验证未登录/切换中拒绝创建、成功记录使用相对句柄、失败和取消仅清当前 lease 临时文件
- [x] 5.7 增加文件 instrumentation，在 A/B 相同订单和相同文件名下保存、重开、清理，验证目录物理分离、路径越界拒绝以及清理任一用户不影响另一用户

## 6. 提醒、Worker、Alarm 与重启恢复

- [x] 6.1 用当前用户 Room reminder repository 替换全局 `PendingOrdersStorage` JSON/SharedPreferences，并用 DAO/repository tests 验证同 orderId 在不同用户数据库独立、未登录访问 fail closed
- [x] 6.2 实现由 `UserTaskIdentity` 派生 WorkManager unique name/tag/input、显式 PendingIntent data URI、通知 ID 和去重 key 的统一 codec，并用单测验证 extras 之外的系统身份真正包含 namespace+epoch+业务 ID
- [x] 6.3 改造 `ServiceTimeNotificationManager` 与 schedule/cancel delegate，使调度、更新和取消只作用于当前 scope+epoch，并用现有及新增 manager tests 验证 A/B 相同 orderId 不互相覆盖或取消
- [x] 6.4 改造 `ServiceTimeEndWorker`、`ServiceTimeAlarmReceiver` 和通知 receiver，在任何副作用前校验当前 scope+epoch+generation，并用 Worker/Receiver tests 验证换号、登出和旧 generation 到期均静默终止
- [x] 6.5 改造 `BootCompletedReceiver` 只在 ACTIVE 会话 namespace Ready 后恢复该用户提醒且不枚举其他数据库，并用重启测试验证损坏会话、PENDING 会话和 B 登录均不会恢复 A 的任务
- [x] 6.6 将倒计时 SharedPreferences、Alarm、全屏/普通通知与关闭动作接入同一任务身份和 cleanup hook，并用 focused tests 验证 requestCode/data/notification 去重不因相同 orderId 冲突
- [x] 6.7 增加后台生命周期 instrumentation，覆盖 A 调度→登出→B 登录→设备重启→旧 Alarm/Worker 触发，验证无 A 通知、无 B 数据删除且系统中不残留 A epoch 的活动任务

## 7. 旧全局存储破坏性切换

- [x] 7.1 建立零 legacy 保留的显式删除/取消清单并实现 `user_storage_namespace_cutover_v1`：取消旧 Worker/Alarm/PendingIntent/通知，删除旧 `app_prefs/app_user`、隐私、设备 ID、登录、兼容性、更新、系统配置、提醒/倒计时/通知偏好、`longcare_database`/sidecar、裸 userId DataStore、共享业务/临时目录、缓存和可识别遗留下载；用预置测试验证全部旧值消失且清理不递归删除应用根目录
- [x] 7.2 将 cutover marker 放入新版本专用设备文件，只在全部步骤成功后原子提交并让每一步可重复；用逐步注入失败的 instrumentation 覆盖所有中断点，验证下次启动可从头继续、不误删新 namespace/新会话文件且不出现半清理可用状态
- [x] 7.3 在会话 Repository、网络设备身份、用户存储注册表和后台恢复初始化前接入 cutover 启动门；用升级安装测试验证完成后处于未同意隐私、未登录、无设备标识、无活动 namespace，旧 DAO、会话和任务从未短暂可见
- [x] 7.4 将新版本设备标识改为隐私同意后生成并内部保存的应用私有 GUID，移除从旧值或硬件标识恢复的路径；用隐私门禁测试验证同意前不访问标识、同意后同一安装稳定、冷切换后重新初始化
- [x] 7.5 更新现有 `LongCareDatabaseMigrationTest` 为已确认的每用户破坏性重建/切换测试，验证 schema 不兼容仅重建目标用户并在测试名与长期文档中明确这是用户确认的例外策略

## 8. WebView 无 host 白名单导航

- [x] 8.1 在跨两入口可复用的 Android 层提取 `WebUrlPolicy`，用表驱动单测验证任意 HTTPS/HTTP host、子域、IP、端口和跨域 URL 通过，空 host、畸形及 file/content/javascript/intent/data/未知 scheme 阻断
- [x] 8.2 将全屏 `WebViewScreen` 接入共享 policy，令允许导航从 `shouldOverrideUrlLoading` 返回 false，并实现主文档错误/SSL 失败/重试状态；用 focused tests 验证不按 host 比较、不在回调中重复 `loadUrl` 且重试原 URL
- [x] 8.3 将 `PrivacyConsentDialog` 接入同一 policy 和失败/重试语义，保持 JavaScript 关闭，并用 focused tests 验证它与业务 WebView 对相同跨域/危险 URL 给出一致结果
- [x] 8.4 增加 WebView 安全配置测试，验证两处始终关闭 file/content/file-URL 跨域访问、拒绝混合内容和 SSL 绕过、无原生 bridge、Safe Browsing 保持启用，并确认 `network_security_config.xml` 未改为全局允许明文

## 9. 架构门禁与长期文档

- [x] 9.1 增加可执行架构门禁及其 shell fixture tests，只允许 registry/factory 创建用户 Room/DataStore、禁止重新引入全局业务 DAO provider、裸 `user_<userId>` 文件名、无 scope 的用户业务 SharedPreferences 和 orderId-only 后台身份
- [x] 9.2 按 `docs/README.md` 维护矩阵更新系统、数据架构、会话生命周期、文件/后台任务、安全和质量文档，明确每用户物理布局、零 legacy 兼容、重新授权/新 GUID/重新登录、服务端重新水合、正常运行期保留策略、破坏性数据库例外及 WebView HTTPS 开放/HTTP 平台约束，并运行 `bash scripts/quality/preflight_local.sh --local-fast`

## 10. 综合验证

- [x] 10.1 运行 `:core:model:test`、`:core:domain:test`、`:core:data:testDebugUnitTest`、相关 feature/app focused unit tests 和 `:core:data:connectedDebugAndroidTest`，修复所有新增失败并保存命令结果作为 CI/PR 证据
- [x] 10.2 运行 `bash scripts/quality/preflight_local.sh --full`、`:app:lintDebug` 和 `:app:assembleDebug`，确认门禁、Lint 与 Debug 构建全绿且没有修改任何厂商 AAR；运行 release validation guard 并确认只保留已记录的厂商生产阻断而未新增阻断
- [x] 10.3 使用 android CLI/adb 在模拟器执行预置旧版全量状态→升级冷切换→重新同意隐私并初始化 GUID→重新登录 A/服务端重新水合→缓存同订单→退出→登录 B 同订单→杀进程/重启→旧通知触发→两个 WebView 跨域/失败重试的 smoke，核对零 legacy 回退、app 私有目录为不同摘要文件且全程无跨用户 UI、数据或任务副作用
