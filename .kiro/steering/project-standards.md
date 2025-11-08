# 长护险项目开发规范

## 项目概述

**项目名称**: 长护险 (Long Care)  
**包名**: com.ytone.longcare  
**技术栈**: Kotlin + Jetpack Compose + Android  
**架构模式**: MVVM + Clean Architecture  
**依赖注入**: Hilt  
**最低SDK**: 24 (Android 7.0)  
**目标SDK**: 34 (Android 14)  
**编译SDK**: 36

## 项目简介

长护险是一款面向养老护理服务的Android应用，主要功能包括：
- 护理人员登录与身份认证
- 服务订单管理（开始、执行、结束）
- NFC签到打卡
- 人脸识别验证（腾讯云人脸识别SDK）
- 位置追踪与定位（高德地图SDK）
- 照片上传与水印处理
- 服务倒计时与提醒
- 护理记录与工时统计

## 核心技术栈

### UI层
- **Jetpack Compose**: 现代化声明式UI框架
- **Material Design 3**: UI设计规范
- **Navigation Compose**: 类型安全的导航
- **Coil 3**: 图片加载库

### 数据层
- **Room**: 本地数据库
- **DataStore**: 轻量级数据存储
- **Retrofit**: 网络请求
- **Moshi**: JSON序列化
- **OkHttp**: HTTP客户端

### 业务逻辑层
- **Hilt**: 依赖注入
- **Coroutines**: 异步编程
- **Flow**: 响应式数据流
- **ViewModel**: 生命周期感知的数据管理

### 第三方SDK
- **腾讯云人脸识别SDK**: 人脸验证
- **腾讯云COS**: 对象存储
- **高德地图SDK**: 定位服务
- **ML Kit Face Detection**: 人脸检测
- **CameraX**: 相机功能

## 代码规范

### 1. 语言要求
- **使用中文回答和注释**: 所有代码注释、文档、提交信息必须使用中文
- **符合Google开发规范**: 遵循[Kotlin官方编码规范](https://kotlinlang.org/docs/coding-conventions.html)和[Android开发最佳实践](https://developer.android.com/kotlin/style-guide)

### 2. 命名规范

#### 包名规范
```
com.ytone.longcare
├── api/                    # API接口定义
│   ├── request/           # 请求模型
│   └── response/          # 响应模型
├── app/                   # Application类
├── common/                # 通用工具类
│   ├── constants/         # 常量定义
│   ├── event/            # 事件总线
│   ├── network/          # 网络相关
│   ├── security/         # 安全相关
│   ├── utils/            # 工具类
│   └── viewmodel/        # 基础ViewModel
├── core/                  # 核心模块
│   └── navigation/       # 导航常量
├── data/                  # 数据层实现
│   ├── cos/              # COS相关
│   ├── repository/       # Repository实现
│   └── storage/          # 本地存储
├── di/                    # 依赖注入模块
├── domain/                # 领域层
│   ├── cos/              # COS领域
│   ├── faceauth/         # 人脸认证
│   ├── identification/   # 身份识别
│   ├── location/         # 定位
│   ├── login/            # 登录
│   ├── order/            # 订单
│   ├── profile/          # 个人信息
│   ├── repository/       # Repository接口
│   ├── system/           # 系统
│   └── userlist/         # 用户列表
├── features/              # 功能模块
│   ├── countdown/        # 倒计时
│   ├── face/             # 人脸相关
│   ├── facecapture/      # 人脸捕获
│   ├── facerecognition/  # 人脸识别
│   ├── home/             # 首页
│   ├── identification/   # 身份认证
│   ├── location/         # 定位
│   ├── login/            # 登录
│   ├── maindashboard/    # 主面板
│   ├── nfc/              # NFC
│   ├── nursing/          # 护理
│   ├── nursingexecution/ # 护理执行
│   ├── photoupload/      # 照片上传
│   ├── profile/          # 个人资料
│   ├── selectdevice/     # 选择设备
│   ├── selectservice/    # 选择服务
│   ├── servicecomplete/  # 服务完成
│   ├── servicecountdown/ # 服务倒计时
│   ├── servicehours/     # 服务工时
│   ├── serviceorders/    # 服务订单
│   ├── shared/           # 共享组件
│   ├── update/           # 更新
│   ├── userdetail/       # 用户详情
│   ├── userlist/         # 用户列表
│   └── userservicerecord/# 用户服务记录
├── model/                 # 数据模型
├── navigation/            # 导航配置
├── network/               # 网络层
├── presentation/          # 展示层
├── shared/                # 共享ViewModel
├── theme/                 # 主题配置
├── ui/                    # UI组件
└── worker/                # 后台任务
```

#### 类命名规范
- **Activity**: `XxxActivity`
- **Fragment**: `XxxFragment`
- **ViewModel**: `XxxViewModel`
- **Repository**: `XxxRepository` (接口) / `XxxRepositoryImpl` (实现)
- **Screen (Compose)**: `XxxScreen`
- **Dialog**: `XxxDialog`
- **Adapter**: `XxxAdapter`
- **Manager**: `XxxManager`
- **Helper**: `XxxHelper`
- **Utils**: `XxxUtils`

#### 变量命名规范
- **常量**: 全大写下划线分隔 `CONSTANT_NAME`
- **变量**: 小驼峰 `variableName`
- **函数**: 小驼峰 `functionName()`
- **类/接口**: 大驼峰 `ClassName`
- **包名**: 全小写 `packagename`

### 3. 架构规范

#### MVVM + Clean Architecture
```
UI Layer (Compose)
    ↓
ViewModel Layer
    ↓
Domain Layer (Use Cases / Repository Interfaces)
    ↓
Data Layer (Repository Implementations)
    ↓
Data Sources (Remote API / Local Database)
```

#### 模块职责
- **UI Layer**: 仅负责UI展示和用户交互，使用Jetpack Compose
- **ViewModel**: 持有UI状态，处理用户事件，调用Domain层
- **Domain Layer**: 定义业务逻辑接口（Repository接口）
- **Data Layer**: 实现Repository接口，处理数据获取和存储
- **DI Layer**: 使用Hilt进行依赖注入

### 4. Compose开发规范

#### Composable函数命名
```kotlin
// ✅ 正确：大驼峰命名
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit
) { }

// ❌ 错误：小驼峰命名
@Composable
fun loginScreen() { }
```

#### 状态管理
```kotlin
// ✅ 使用ViewModel持有状态
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    MyScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

// ✅ 分离有状态和无状态Composable
@Composable
private fun MyScreenContent(
    uiState: MyUiState,
    onEvent: (MyEvent) -> Unit
) { }
```

#### 参数顺序
```kotlin
@Composable
fun MyComposable(
    // 1. 必需参数
    title: String,
    // 2. 可选参数（带默认值）
    subtitle: String? = null,
    // 3. 回调函数
    onClick: () -> Unit = {},
    // 4. Modifier（总是最后）
    modifier: Modifier = Modifier
) { }
```

### 5. 国际化 (I18n)

**所有用户可见文本必须定义在 `strings.xml` 中**

```xml
<!-- ✅ 正确 -->
<string name="login_button">登录</string>
<string name="error_network">网络连接失败</string>
```

```kotlin
// ✅ 正确：使用字符串资源
Text(text = stringResource(R.string.login_button))

// ❌ 错误：硬编码字符串
Text(text = "登录")
```

### 6. 无障碍 (A11y)

**所有交互元素必须设置 `contentDescription`**

```kotlin
// ✅ 正确
Icon(
    imageVector = Icons.Default.Close,
    contentDescription = stringResource(R.string.close_button),
    modifier = Modifier.clickable { onClose() }
)

// ✅ 装饰性图片清除语义
Image(
    painter = painterResource(R.drawable.decoration),
    contentDescription = null,
    modifier = Modifier.clearAndSetSemantics { }
)

// ✅ 自定义语义信息
Box(
    modifier = Modifier.semantics {
        contentDescription = "播放按钮"
        role = Role.Button
    }
)
```

### 7. 依赖注入规范

#### ViewModel注入
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() { }
```

#### Repository注入
```kotlin
class MyRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val database: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MyRepository { }
```

#### Module定义
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMyRepository(impl: MyRepositoryImpl): MyRepository
}
```

### 8. 协程使用规范

#### ViewModel中使用
```kotlin
// ✅ 使用viewModelScope
fun loadData() {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        try {
            val result = repository.getData()
            _uiState.update { it.copy(data = result, isLoading = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message, isLoading = false) }
        }
    }
}
```

#### Repository中使用
```kotlin
// ✅ 使用withContext切换线程
override suspend fun getData(): Result<Data> = withContext(ioDispatcher) {
    try {
        val response = apiService.getData()
        Result.success(response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 9. 错误处理规范

#### 网络请求错误处理
```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val message: String) : ApiResult<Nothing>()
    data class Exception(val throwable: Throwable) : ApiResult<Nothing>()
}
```

#### UI状态错误处理
```kotlin
data class UiState<T>(
    val data: T? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
```

### 10. 测试规范

#### 单元测试
- 测试文件放在 `app/src/test/kotlin/` 目录
- 测试类命名：`XxxTest`
- 使用 JUnit 4 和 MockK

#### UI测试
- 测试文件放在 `app/src/androidTest/kotlin/` 目录
- 使用 Compose Testing API

### 11. 代码注释规范

```kotlin
/**
 * 用户登录Repository
 * 
 * 负责处理用户登录相关的数据操作，包括：
 * - 手机号验证码登录
 * - 登录状态持久化
 * - 用户信息缓存
 * 
 * @property apiService API服务接口
 * @property dataStore 数据存储
 */
class LoginRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    private val dataStore: AppDataStore
) : LoginRepository {
    
    /**
     * 发送短信验证码
     * 
     * @param phone 手机号码
     * @return 发送结果
     */
    override suspend fun sendSmsCode(phone: String): ApiResult<Unit> {
        // 实现代码
    }
}
```

### 12. Git提交规范

#### 提交信息格式
```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Type类型
- `feat`: 新功能
- `fix`: 修复bug
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 重构
- `perf`: 性能优化
- `test`: 测试相关
- `chore`: 构建/工具链相关

#### 示例
```
feat(login): 添加手机号验证码登录功能

- 实现发送验证码接口
- 实现验证码登录接口
- 添加登录状态管理

Closes #123
```

## 项目特定规范

### 1. NFC功能
- NFC相关代码集中在 `features/nfc/` 和 `common/utils/NfcManager.kt`
- 使用 `AppEventBus` 发送NFC事件
- 支持三种NFC技术：NDEF、TECH、TAG

### 2. 人脸识别
- 使用腾讯云人脸识别SDK
- 人脸识别流程：获取FaceId → 启动SDK → 验证结果
- 相关代码在 `features/facerecognition/` 和 `domain/faceauth/`

### 3. 位置追踪
- 使用高德地图SDK
- 支持前台服务持续定位
- 相关代码在 `features/location/`

### 4. 照片上传
- 支持相机拍照和相册选择
- 自动添加水印（时间、地点、用户信息）
- 上传到腾讯云COS
- 相关代码在 `features/photoupload/`

### 5. 服务倒计时
- 使用前台服务保持倒计时
- 支持精确闹钟提醒
- 相关代码在 `features/servicecountdown/`

## 安全规范

### 1. 数据加密
- 敏感数据使用 `CryptoUtils` 加密
- 使用 DataStore 存储加密后的数据

### 2. 网络安全
- 所有API请求使用HTTPS
- 使用 `RequestInterceptor` 添加请求头和签名

### 3. 权限管理
- 使用 `UnifiedPermissionHelper` 统一管理权限请求
- 运行时权限检查

## 性能优化

### 1. 图片加载
- 使用Coil 3异步加载图片
- 配置内存缓存和磁盘缓存

### 2. 列表优化
- 使用LazyColumn/LazyRow
- 使用key参数优化重组

### 3. 协程优化
- 合理使用Dispatchers
- 避免在主线程执行耗时操作

## 调试与日志

### 1. 日志工具
- 使用 `LogExt.kt` 中的扩展函数
- `logD()`, `logE()`, `logI()`, `logW()`

### 2. 崩溃日志
- 使用 `CrashLogManager` 记录崩溃日志
- 崩溃日志保存在应用私有目录

## 构建与发布

### 1. 构建配置
- Debug版本：使用测试环境API，启用Mock数据
- Release版本：使用生产环境API，启用代码混淆

### 2. APK命名
- 格式：`app-v{version}-{date}-{versionCode}-{buildType}.apk`
- 示例：`app-v1.0-250108-10-release.apk`

### 3. 签名配置
- 使用项目根目录的 `keystore.jks`
- 签名信息在 `build.gradle.kts` 中配置

## 参考资源

- [Kotlin官方文档](https://kotlinlang.org/docs/home.html)
- [Android开发者文档](https://developer.android.com/)
- [Jetpack Compose文档](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Hilt文档](https://dagger.dev/hilt/)
