## 1. 第一方 Mock 安全边界

- [x] 1.1 在 `:app` Debug source set 建立以 HTTP method 和规范化 encoded path 为键的单一 Mock 路由登记表，并用 focused unit test 验证键唯一、大小写/方法精确且 query 不会隐式改变路由选择
- [x] 1.2 将现有 `MockInterceptor` 的 path 分支迁入登记表且不改变已登记响应契约，并用回归测试逐项验证迁移前已有的 method/path 都能命中对应 fixture
- [x] 1.3 增加可分类的 Debug 缺失路由 `IOException`，令显式 Mock 下的未知路径和方法不匹配均在本地失败，并用 recording chain 测试验证两种情况的 `proceed` 调用次数均为零
- [x] 1.4 保留 `USE_MOCK_DATA=false` 时的真实请求透传，并用 recording chain 测试验证请求只被 `proceed` 一次且未被本地响应改写
- [x] 1.5 对缺失路由诊断增加脱敏测试，验证消息只含 method 与 encoded path，且不含授权头、cookie、token、query、请求体或用户数据

## 2. 确定性场景与 fixture 契约

- [x] 2.1 引入仅供 Debug/Test 使用且具有稳定默认值的场景 provider，移除订单结束状态等随机响应，并用重复执行测试验证同一输入始终选择相同分支
- [x] 2.2 为 `/V1/User/GetFace` 登记正确 method/path 及“未登记”“已登记”两套模型有效 fixture，并用 focused tests 验证两套显式场景分别进入既有设置与核验分支
- [x] 2.3 修复系统配置 fixture 的无效密钥/格式字段并增加业务语义校验，运行契约测试验证 JSON 可解析、十六进制与运行时解密输入格式有效且错误 fixture 会明确失败
- [x] 2.4 清理版本检查与上传凭据 fixture：默认版本不得触发更新，测试凭据必须明确非生产且时间/字段自洽，并用模型解析与语义测试验证不会暴露可用生产 secret 或真实 APK 下载地址
- [x] 2.5 建立登录、服务单、系统启动/配置、版本、文件元数据和人脸接口的安全冒烟 route-key 清单，并用覆盖测试验证每项都有 route、fixture/生成器和 validator

## 3. 第三方调用与关键流程测试边界

- [x] 3.1 复用现有照片云上传器抽象，在显式 Mock Debug 下绑定不初始化 COS SDK 的确定性 fake、在真实 Debug/Release 下保持真实实现，并用 DI/focused test 验证三种模式的绑定与零第三方调用
- [x] 3.2 在 `androidTest` 通过既有身份核验业务边界提供测试所有的已验证状态，覆盖核验后导航与倒计时，并用 Release source/产物检查证明不存在可调用的人脸或活体绕过
- [x] 3.3 补充版本 Worker 的无更新/有更新输出及更新提示 focused tests，在有更新场景替换下载边界或止于下载前，并验证测试过程中没有访问 fixture URL 或创建真实下载任务
- [x] 3.4 增加边界回归测试，验证第一方 Mock 不接管 AMap/厂商 SDK 客户端且不新增 WebView host 限制；以现有 WebView 任意业务 host 导航测试和离线地图可恢复失败作为验收

## 4. 构建配置与变体隔离

- [x] 4.1 将 `debug.useMockData` 的仓库 fallback 改为 `false`，增加可观察最终模式的无敏感信息构建输出/检查，并分别构建默认、显式 `true`、显式 `false` 的 Debug 验证生成的 `USE_MOCK_DATA` 值
- [x] 4.2 扩展 release validation guard 检查 Debug Mock 类、assets、场景 provider、上传 fake 与可启用入口均未进入 Release，运行该门禁并验证 Release 的 `USE_MOCK_DATA=false`
- [x] 4.3 为 performance/profile 网络拦截器选择与顺序增加 focused test，验证专用离线策略仍优先且不会执行 Debug Mock 路由
- [x] 4.4 将路由/fixture 契约、模式一致性和 Release 隔离检查接入现有质量脚本或 CI 对应阶段，并以一次目标 job/本地等价命令成功且故意破坏 fixture 时能失败来验证门禁有效

## 5. 长期文档与完整验证

- [x] 5.1 按 `docs/README.md` 维护矩阵同步根 README、技术栈、系统概览、CI 门禁与开放缺口，明确默认关闭、显式启用、fail-closed、第三方边界和 WebView 不限 host，并用文档/构建配置一致性检查验证无陈旧描述
- [x] 5.2 运行 `:app` 受影响的 Debug unit/instrumentation focused tests，验证路由安全、fixture、上传 fake、版本更新和身份后续流程全部通过
- [x] 5.3 运行 `bash scripts/quality/preflight_local.sh --full`、`:app:lintDebug`、显式 Mock `:app:assembleDebug` 及现有 release validation guard，修复本 change 引入的问题并保存终端/CI 结果作为验收证据
- [x] 5.4 在专用 API 36 设备上确认安装包为显式 Mock 后保持外网不可用，执行约定的登录、服务单、人脸真实拒绝、上传 fake、核验后倒计时和版本检查冒烟，验证无第一方真实网络访问且不把第三方替身记录为真实 SDK 成功
- [x] 5.5 核对最终 diff 未修改厂商 AAR、targetSdk、依赖版本、持久化与 WebView host 策略，运行 `openspec validate fail-close-debug-mock-network --type change --strict --no-interactive` 和 `git diff --check` 均通过后再完成 change
