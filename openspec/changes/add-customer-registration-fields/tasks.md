## 1. 接口参数与登记草稿

- [x] 1.1 在业务请求模型、请求 DTO 和 Repository 映射中补齐 `isDisability` 与 `remarks`；扩展 Data 契约测试，验证默认 0、选择 1、空备注和中文/换行的真实 JSON key、类型及原参数不变。
- [x] 1.2 扩展 `SalesCustomerDraft`、`toRequest()` 和现有 Saver；测试默认值、首尾空白处理、新字段保存/恢复、旧六项草稿、重置，以及提交失败后重试保留当前输入，原有登记校验继续通过。

## 2. 登记与确认界面

- [x] 2.1 在登记页加入默认否的是/否单选和备注选填多行框，复用现有样式与字符串资源；离线 Compose 测试验证默认选中、点击文字切换、互斥语义和不填备注可继续。
- [x] 2.2 在登记确认页显示是否残疾及非空备注；离线 UI 测试验证确认内容与请求一致、空备注不展示、返回编辑保留输入、状态恢复以及滚动后继续按钮可达，不调用线上写接口。

## 3. 回归与文档

- [x] 3.1 执行受影响单测、`preflight_local.sh --full`、`:app:lintDebug :app:assembleDebug :app:assembleRelease` 和 Lint warning 守卫；检查无新增敏感内容日志及无关改动，分别记录实际执行、缓存及未做真机/线上验收的边界。
- [x] 3.2 同步产品概览的登记字段说明、必要页面说明和变更状态；文档校验、`openspec validate --all --strict --no-interactive` 与 `git diff --check` 全部通过，不新增过程日志。
