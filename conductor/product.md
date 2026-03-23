# LongCare

> 面向护理/长护服务执行场景的 Android 客户端，覆盖登录、派单服务、身份核验、定位、拍照上传和服务计时流程。

## Problem

一线护理服务执行涉及用户登录、服务单选择、身份确认、到岗定位、服务过程留痕、图片上传和服务结束确认。流程长、状态多、设备能力依赖重，如果把业务流程、平台能力和网络存储逻辑混在一起，代码会迅速失控，且难以满足移动端稳定性与发布要求。

## Solution

项目采用 Android 多模块架构，将产品壳层、领域接口、数据实现、通用 UI 能力与业务 feature 拆开，并通过 Hilt、Room、WorkManager、Compose 和 CI 质量门禁保证主链路可维护、可测试、可发布。

## Target Users

| Persona | Needs | Pain Points |
|---|---|---|
| 护理服务执行人员 | 快速登录、查看待服务订单、按流程完成服务留痕 | 流程长、设备权限复杂、网络不稳定 |
| 运营/项目侧 | 保证服务执行数据完整、流程可追溯 | 人工补录成本高，异常状态难排查 |
| 开发与维护团队 | 在复杂业务下保持架构清晰和发布稳定 | 历史代码集中在 `app`，边界容易回退 |

## Core Features

| Feature | Status | Description |
|---|---|---|
| 登录 | implemented | 手机号登录、短信验证码、会话状态管理 |
| 首页/服务入口 | implemented | 从首页进入服务单、用户列表、服务流程 |
| 身份识别 | implemented | 身份校验、人脸相关配置与识别流程 |
| 定位跟踪 | implemented | 基于高德定位的服务过程定位与上传 |
| 拍照上传 | implemented | 服务前后拍照、水印、上传与任务队列 |
| 服务倒计时 | implemented | 服务进行中倒计时、轮询、提醒、结束处理 |
| 应用更新 | implemented | 启动任务、下载/安装更新、版本提示 |
| 架构持续现代化 | in-progress | 继续把历史业务从 `app` 下沉到 `core/feature` 模块 |

## Success Metrics

| Metric | Target | Current |
|---|---|---|
| Debug 构建可通过 | 必须稳定通过 | 已有本地与 CI 命令 |
| 单元测试与 lint 门禁 | PR 必过 | 已接入 Android CI |
| 模块边界守卫 | 防止 `feature -> data impl` 回退 | 已有脚本守卫 |
| 发布可重复性 | CI 可产出 APK/AAB 与 Release | 已有 release workflow |
| 壳层收敛度 | `app` 仅承载壳层与组装 | 部分完成，仍有大量业务代码留在 `app` |

## Current Product/Code Reality

- 业务域已经较完整，但代码层面仍处于“模块化重构后半段”。
- `:feature:login`、`:feature:home`、`:feature:identification` 已形成入口抽象。
- `:feature:location`、`:feature:photoupload`、`:feature:servicecountdown` 已拆出独立模块，但 `app` 中仍保留大量相关 UI 和流程实现。
- README 中的“当前模块结构”未完全同步新增 feature 模块，说明上下文文档需要持续维护。

## Roadmap

- **Phase 1**: 稳定当前发布链路和架构守卫，避免模块边界回退。
- **Phase 2**: 继续把历史业务 UI、ViewModel、平台实现从 `app` 下沉到 `feature/*` 或 `core/*`。
- **Phase 3**: 在关键服务链路补更高价值的集成测试与回归用例。
