# Login Screen 截图回归 Checklist

## 1. 回归目标

- 验证登录页从 `ConstraintLayout` 重构为 `Box/Column/Row` 后，视觉与交互无回归。
- 验证关键状态（默认、输入、加载、键盘弹起、测试按钮显隐）布局稳定。

## 2. 测试基线

- Branch: 当前待提交分支（包含登录页布局重构变更）
- Build: `./gradlew :app:assembleDebug`
- 包名: `com.ytone.longcare`

## 3. 设备矩阵（最少）

- Phone 小屏: 360x800（如 Pixel 4a / API 34+）
- Phone 大屏: 412x915（如 Pixel 8 / API 35+）
- 横向验证: 仅确认页面被强制竖屏，无法旋转进入横屏布局

## 4. 截图命名规范

- 目录建议: `artifacts/login-regression/YYYYMMDD/`
- 文件命名: `login_<device>_<scene>.png`
- 示例:
  - `login_pixel8_default.png`
  - `login_pixel8_keyboard.png`
  - `login_pixel8_loading.png`

## 5. 场景清单（逐项打勾）

- [ ] `default`: 首次进入登录页，背景图、左上小 Logo、顶部主 Logo 位置正确，无裁切
- [ ] `phone_input`: 输入 11 位手机号后，输入框不抖动，按钮区不遮挡
- [ ] `code_input`: 输入验证码，验证码输入框与“发送验证码”按钮底部对齐
- [ ] `send_code_countdown`: 点击发送验证码后倒计时文本显示完整，无换行错位
- [ ] `login_enabled`: 手机号+验证码有效时，“登录”按钮可点击
- [ ] `loading`: 登录请求中出现圆形进度条，按钮高度不变化
- [ ] `keyboard`: 聚焦验证码输入框，键盘弹起后输入区与登录按钮仍可见
- [ ] `agreement_text`: 《用户协议》《隐私政策》可点击，文本居中换行正常
- [ ] `safe_drawing`: 刘海/状态栏区域不遮挡 Logo 与输入内容
- [ ] `small_screen`: 小屏设备上登录按钮与协议文案不重叠
- [ ] `test_buttons_hidden`: `NfcTestConfig.ENABLE_NFC_TEST=false` 时底部测试按钮不出现
- [ ] `test_buttons_visible`: `NfcTestConfig.ENABLE_NFC_TEST=true` 时底部测试按钮横向可滚动且不遮挡协议文案

## 6. 通过标准

- 所有场景均通过且无明显视觉偏移（间距、对齐、遮挡、裁切）。
- 无崩溃、无输入焦点异常、无点击区域错位。
- 至少保留每个设备 3 张关键截图：`default`、`keyboard`、`loading`。

## 7. 失败记录模板

- 场景:
- 设备/API:
- 复现步骤:
- 实际结果:
- 期望结果:
- 截图路径:
- 是否阻断发布: `是/否`

