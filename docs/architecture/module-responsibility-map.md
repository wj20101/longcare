# Module Responsibility Map

## 目标结构

| 模块 | 责任 | 允许依赖 | 禁止依赖 |
|---|---|---|---|
| `:app` | 应用壳层、导航组装、启动配置 | `:core:*`, `:feature:*` | 业务实现细节 |
| `:core:model` | 通用模型与值对象 | Kotlin stdlib | Android framework |
| `:core:domain` | 用例、Repository 接口、领域规则 | `:core:model` | Data 实现、Android framework |
| `:core:data` | Repository 实现、数据源访问 | `:core:domain`, `:core:model` | Feature/UI |
| `:core:ui` | 主题、通用 UI 组件（包括统一图片预览） | `:core:model` | Data 实现 |
| `:core:common` | 通用基础能力（日志、错误模型、调度器、统一图片处理与受管文件） | `:core:model` | Feature |
| `:feature:login` | 登录业务 UI 与状态编排 | `:core:domain`, `:core:ui`, `:core:model` | Data 实现 |
| `:feature:home` | 首页业务 UI 与状态编排 | `:core:domain`, `:core:ui`, `:core:model` | Data 实现 |
| `:feature:identification` | 身份识别业务 UI 与状态编排 | `:core:domain`, `:core:ui`, `:core:model` | Data 实现 |
| `:feature:photoupload` | 图片任务编排、统一云端上传门面 | `:core:common`, `:core:domain`, `:core:model` | App 路由/UI 实现 |

## 图片能力边界

- `:core:common` 的 `UnifiedImagePipeline` 是应用自有图片输出的唯一入口，负责方向修正、水印合成、JPEG 压缩、原子写入、大小校验和受管文件清理。
- `:core:common` 的 `ImageProcessingPolicies` 统一维护水印照片与人脸照片的尺寸、质量及 10 MB 上限，业务层不得再硬编码压缩参数。
- `:core:ui` 的 `PhotoPreviewDialog` 是全屏预览的唯一实现，统一支持 `Uri`、`File`、URL 与 `Bitmap`，并提供缩放、拖动、双击和关闭行为。
- `:feature:photoupload` 的 `PhotoCloudUploader` 统一封装图片 COS 上传结果校验；拍照页面、销售登记和照片任务队列只依赖该门面。
- `:core:data` 的订单图片仓库在删除数据库记录时同步清理对应受管文件，服务完成、单张删除与整单清理不再依赖各调用方手动补偿。
- 标准业务照片统一进入 `CameraRoute`。人脸检测保留专用 CameraX 分析界面，但持久化、输出压缩和全屏预览仍复用上述共享能力。

## 当前迁移优先级

1. 第一批：`:feature:identification`（当前复杂度最高，收益最大）。
2. 第二批：`:feature:login`、`:feature:home`。
3. 第三批：其余 feature 模块按业务耦合度递进迁移。

## 迁移判定标准

1. Feature 内不再出现 `data.repository.*Impl` 引用。
2. `:app` 不承载业务流程实现。
3. Repository 接口由 Domain 暴露，Data 仅实现。
