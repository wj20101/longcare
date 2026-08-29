# ADR-002：复合用户物理隔离与零 Legacy 冷切换

状态：已接受  
最后核对：2026-08-29

## 决策

LongCare 只允许一个活动登录会话，但每个 `companyId + accountId + userId` 复合身份拥有独立且不透明的 v1 命名空间。SHA-256 摘要只用于稳定路径索引，不是加密或认证手段。当前物理布局为：

```text
databases/longcare_user_v1_<sha256>.db
files/datastore/user_v1_<sha256>.preferences_pb
files/user_scopes/v1/<sha256>/namespace.json
files/user_scopes/v1/<sha256>/persistent/<purpose>/...
cache/user_scopes/v1/<sha256>/session/<purpose>/...
no_backup/session/longcare_session_v1.preferences_pb
shared_prefs/longcare_device_state_v1.xml
```

命名空间描述文件、Room metadata 和用户 DataStore metadata 必须一致；不一致时 fail closed。Repository 只能通过当前 `scope + sessionEpoch + generation` 租约访问 Room、DataStore 或受管文件。退出和换号先撤销旧租约、停止实时/后台能力并关闭旧 Room，新命名空间 Ready 后才发布登录态。

升级到该模型时执行一次 `user_storage_namespace_cutover_v1`。切换使用新版本专用设备文件中的原子 marker，并在 marker 提交前幂等取消旧 Work/Alarm/PendingIntent/通知，删除旧明文会话、隐私状态、设备 ID、登录/兼容/更新偏好、全局业务数据库、无复合归属的 DataStore、共享业务文件、缓存与可识别下载。旧值不读取、不解码、不迁移、不重加密、不回退。升级后重新展示隐私协议、生成新的应用私有 GUID，并要求重新登录后从服务端重新水合。

新会话使用 `noBackupFilesDir/session/longcare_session_v1.preferences_pb` 中的整体 AES-GCM 密文；完整 Token 和身份证号只停留在数据层的目的受限快照中。普通 UI、导航、后台任务输入只取得安全 `CurrentUser` 或用户任务身份。

## 已确认的破坏性数据库例外

每用户 Room 当前 schema 版本为 7，并有 1–7 的导出 schema。用户已明确确认本地垃圾数据无需兼容，因此 `UserDatabaseFactory` 保留 `fallbackToDestructiveMigration(dropAllTables = true)`：schema 不兼容时只重建目标用户的独立数据库文件，不能删除或重建其他用户文件。服务端是订单与可恢复配置的事实来源；重新登录后由重新水合流程填充。

这是仓库“持久化变更优先显式 Migration”一般规则的有意、限定例外。普通关闭/重开必须保留当前 schema 数据；破坏性行为仅发生在不兼容 schema 打开目标用户文件时。`LongCareDatabaseMigrationTest` 同时验证目标重建和另一用户数据不受影响。

## 保留与安全边界

- 正常退出保留当前用户 Room、用户 DataStore 和 `persistent` 文件；清除会话秘密、临时凭据、session 文件与当前 epoch 后台任务。
- 同一安装内重新登录相同复合账号复用其命名空间；登录其他复合账号只打开另一摘要文件。服务端重新水合只写当前有效 lease，失败时不借用其他用户或 legacy 快照。
- 分文件提供账号逻辑隔离和更安全的清理边界，但不等于静态加密。Room、用户 DataStore 和业务文件仍主要依赖 Android 应用沙箱。
- 设备 GUID 只能在隐私同意后以 `UUID.randomUUID()` 生成并保存，不从 Android ID、硬件 ID 或 legacy 值恢复。
- 厂商 AAR、服务端契约和生产网络配置不在此决策中修改。

## 验证

- JVM/Robolectric：namespace、metadata、registry 并发、过期租约、会话 ACTIVE/PENDING、故障注入、GUID 隐私门禁和账号切换延迟回调。
- Instrumentation：双用户 Room/文件隔离、全部 cutover 中断点重试、平台任务取消、单用户破坏性重建及后台重启生命周期。
- 架构门禁：禁止恢复全局业务 DAO/DataStore、裸用户文件名、无 scope 的业务 SharedPreferences 和 orderId-only 后台身份。
