## Context

动机与范围见 [proposal.md](proposal.md)。当前基线 `f1fb3648`：登记与确认 UI 位于 `SalesRegistrationScreens.kt`；`SalesCustomerDraft` 定义于 `SalesViewModel.kt`，经 `toRequest()`、`SaleRepositoryImpl.toDto()` 提交；`SalesExperienceScreen.kt` 的 `salesCustomerDraftSaver` 保存现有六项文本。

2026-09-21 已读取[在线 Swagger](https://careapi.ytone.cn/swagger/v1/swagger.json)：`AddUserLatentParamModel.isDisability` 为 int32（1 是、0 否），`remarks` 为 nullable string，未声明备注长度上限。详情响应也有两项，但本次仅完善登记与提交确认，不扩展已登记客户详情页。

## Goals / Non-Goals

**Goals:** 保持现有草稿为表单与确认页的共同来源，贯通 App、Model、Data 的两个字段，补齐可验证的默认值与恢复行为。

**Non-Goals:** 不建设通用动态表单、枚举注册器或新持久层；不迁移模块、不改评估/导航协议，不额外收集证明材料或加入未知的业务校验。

## Decisions

### 1. 直接扩展现有请求链路

草稿采用布尔值表示是否残疾（默认 false），`toRequest()` 显式映射为整数 0/1；业务请求模型与网络 DTO 使用接口原名 `isDisability`、`remarks`，默认分别为 0 和空字符串，Repository 直接传递。

备注沿用现有文本字段的 `trim()` 处理，保留内部换行，不新增必填或未经接口约定的长度限制。使用空字符串提交未填备注，与现有可选文本约定一致；不引入全局 null 适配器或新的包装模型。API 路径、响应与其他参数不变。

### 2. 复用界面样式与状态管理

在现有地址输入后加入是否残疾单选和备注输入，照片/定位区域保持原顺序。单选采用已有 Material 3 依赖，整行文字可点击，并提供单选组语义；已通过 Android CLI 核对[官方 RadioButton 文档](https://developer.android.com/develop/ui/compose/components/radio-button)中的 `selectableGroup` / `selectable` 用法。备注复用 `SalesRegistrationField` 的多行模式，不引入新 UI 框架。

确认页读取同一草稿，展示“是否残疾：是/否”，仅在 `remarks.trim()` 非空时显示备注。新文案进入字符串资源，颜色、间距沿用现有页面。

### 3. 在现有 Saver 尾部追加字段

继续使用当前 `rememberSaveable` 和 Saver，将布尔值与备注追加到现有六项之后；恢复新增项时使用缺省值，使旧六项草稿可按否/空备注恢复。仅增加这两个字段的缺省读取，不建立版本化迁移框架。

表单返回、拍照/定位往返、确认页返回编辑和失败重试继续共用草稿。现有 `SalesCustomerDraft()` 重置路径自然得到否和空备注，不改变原有清空时机。

### 4. 沿现有测试边界验证

- Data：Repository 映射及真实 Moshi JSON key/类型验证，覆盖 0/1、空备注、中文/换行，不丢失已有参数。
- App：草稿默认值、`toRequest()`、既有校验、提交失败保留/重试、Saver 往返与旧六项输入。
- 离线 Compose：默认选中否、是/否互斥、点击文字、可选备注、确认展示与返回编辑；使用合成数据，不调用线上写接口。
- 运行受影响单测、完整本地 preflight、Lint 和 Debug/Release 构建；构建结果不冒充真机或线上提交验收。

## Risks / Trade-offs

- [Saver 漏字段导致页面恢复丢失输入] → 同时更新保存和恢复，测试非默认值及旧草稿。
- [接口类型或字段名偏差] → 按在线 Schema 用整数与准确 JSON key，覆盖实际序列化及提交参数。
- [备注或残疾状态泄露] → 不新增这些内容的日志、诊断附件或分析上报，不用真实客户资料编写测试。
- [表单新增高度影响操作] → 复用滚动容器，离线 UI 验证新增项和继续按钮可达；真机测试另行安排，不自动提交客户。

## Migration Plan

无需数据库、权限或依赖迁移。先完成模型/映射和专项测试，再补草稿与界面，最后完成构建和文档验证；旧草稿新增字段按默认值恢复。回滚只撤销本次客户端字段与 UI，不改服务端已存客户数据。
