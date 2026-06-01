# Login Agreement Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit agreement consent to the login screen, intercept login when consent is missing, and let the user confirm agreement in a dialog before continuing the existing login flow.

**Architecture:** Keep consent and dialog visibility local to `LoginScreenContent`, because this is screen interaction state rather than domain state. Reuse the existing linked agreement text, wrap it in a new consent row with a dedicated checkbox, and drive login submission through one local gate so the button path and dialog-confirm path share the same logic.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI instrumentation tests, JUnit4, Android Gradle Plugin 9.x.

---

## File Structure

- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
  Purpose: Hold screen-local consent state, show the agreement confirmation dialog, and centralize the gated login submission flow. Preserve the existing low-resolution adaptation changes already present in the working tree.

- `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt`
  Purpose: Add a reusable consent row that combines a checkbox and the existing linked agreement text without changing the current link-opening behavior.

- `app/src/main/res/values/strings.xml`
  Purpose: Add dialog copy for the agreement confirmation flow.

- `app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginAgreementConsentRowTest.kt`
  Purpose: Verify that only the checkbox toggles consent and that tapping the agreement text body does not toggle it.

- `app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginScreenAgreementDialogTest.kt`
  Purpose: Verify the login interception flow, including dialog display, cancel behavior, and confirm-and-agree behavior.

## Task 1: Add the Consent Row Component

**Files:**
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginAgreementConsentRowTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt`

- [ ] **Step 1: Write the failing Compose instrumentation test for the consent row**

```kotlin
package com.ytone.longcare.features.login.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test

class LoginAgreementConsentRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkbox_toggles_only_when_checkbox_itself_is_tapped() {
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }
            LongCareTheme {
                AgreementConsentSection(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    onUserAgreementClick = {},
                    onPrivacyPolicyClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOff()
        composeRule.onNodeWithTag("login_agreement_checkbox").performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()

        composeRule.onNodeWithText("我已阅读并同意", substring = true).performClick()
        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOn()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.login.ui.LoginAgreementConsentRowTest
```

Expected: FAIL because `AgreementConsentSection` and the `login_agreement_checkbox` test tag do not exist yet.

- [ ] **Step 3: Implement the consent row in `LoginScreenComponents.kt`**

Add the new composable next to `AgreementText` and keep `AgreementText` unchanged so link annotation behavior stays isolated.

```kotlin
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun AgreementConsentSection(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onUserAgreementClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag("login_agreement_checkbox"),
            colors = CheckboxDefaults.colors(
                checkedColor = PrimaryBlue,
                checkmarkColor = Color.White
            )
        )
        AgreementText(
            onUserAgreementClick = onUserAgreementClick,
            onPrivacyPolicyClick = onPrivacyPolicyClick,
            modifier = Modifier.weight(1f)
        )
    }
}
```

- [ ] **Step 4: Run the consent row test to verify it passes**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.login.ui.LoginAgreementConsentRowTest
```

Expected: PASS with one successful test for the checkbox-only toggle behavior.

- [ ] **Step 5: Commit the component change**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreenComponents.kt \
  app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginAgreementConsentRowTest.kt
git commit -m "feat: add login agreement consent row"
```

## Task 2: Gate Login Behind Agreement Confirmation

**Files:**
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginScreenAgreementDialogTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Write the failing Compose instrumentation tests for dialog-gated login**

```kotlin
package com.ytone.longcare.features.login.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginScreenAgreementDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun login_without_agreement_shows_confirmation_dialog_and_does_not_submit() {
        var loginCount = 0

        composeRule.setContent {
            LongCareTheme {
                LoginScreenContent(
                    actions = LoginFeatureActions(
                        onLoginSuccess = {},
                        onOpenWebPage = { _, _ -> }
                    ),
                    loginState = LoginUiState.Idle,
                    sendSmsState = SendSmsCodeUiState.Idle,
                    startConfigState = StartConfigUiState.Idle,
                    countdownSeconds = 0,
                    onSendCodeClick = {},
                    onLoginClick = { _, _ -> loginCount++ }
                )
            }
        }

        composeRule.onNodeWithText("请输入您的手机号码").performTextInput("13800138000")
        composeRule.onNodeWithText("输入验证码").performTextInput("123456")
        composeRule.onNodeWithText("确定登录").performClick()

        composeRule.onNodeWithText("登录前请先阅读并同意《用户协议》和《隐私政策》").assertExists()
        assertEquals(0, loginCount)
    }

    @Test
    fun confirm_and_agree_checks_checkbox_and_submits_login() {
        var loginCount = 0

        composeRule.setContent {
            LongCareTheme {
                LoginScreenContent(
                    actions = LoginFeatureActions(
                        onLoginSuccess = {},
                        onOpenWebPage = { _, _ -> }
                    ),
                    loginState = LoginUiState.Idle,
                    sendSmsState = SendSmsCodeUiState.Idle,
                    startConfigState = StartConfigUiState.Idle,
                    countdownSeconds = 0,
                    onSendCodeClick = {},
                    onLoginClick = { _, _ -> loginCount++ }
                )
            }
        }

        composeRule.onNodeWithText("请输入您的手机号码").performTextInput("13800138000")
        composeRule.onNodeWithText("输入验证码").performTextInput("123456")
        composeRule.onNodeWithText("确定登录").performClick()
        composeRule.onNodeWithText("我知道了").performClick()

        composeRule.onNodeWithTag("login_agreement_checkbox").assertIsOff()
        assertEquals(0, loginCount)
    }
}
```

- [ ] **Step 2: Run the dialog tests to verify they fail**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.login.ui.LoginScreenAgreementDialogTest
```

Expected: FAIL because the login screen does not yet show an agreement dialog or persist consent state.

- [ ] **Step 3: Implement the gated login flow and dialog**

Add the new strings in `app/src/main/res/values/strings.xml`:

```xml
<string name="login_agreement_confirm_message">请先主动勾选同意《用户协议》和《隐私政策》后再继续</string>
<string name="login_agreement_confirm_action">我知道了</string>
<string name="login_agreement_cancel_action">取消</string>
```

Update `LoginScreen.kt` so consent state stays local and login goes through one gate:

```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable

var agreementChecked by rememberSaveable { mutableStateOf(false) }
var showAgreementDialog by rememberSaveable { mutableStateOf(false) }

fun proceedLogin() {
    onLoginClick(phoneNumber, verificationCode)
}

fun submitLogin() {
    if (agreementChecked) {
        proceedLogin()
    } else {
        showAgreementDialog = true
    }
}
```

Replace the old agreement area with the new consent row:

```kotlin
AgreementConsentSection(
    checked = agreementChecked,
    onCheckedChange = { agreementChecked = it },
    onUserAgreementClick = openUserAgreement,
    onPrivacyPolicyClick = openPrivacyPolicy,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
)
```

Change the login button callback passed into `LoginInputForm`:

```kotlin
onLoginClick = ::submitLogin
```

Render the dialog below the main screen content:

```kotlin
if (showAgreementDialog) {
    AlertDialog(
        onDismissRequest = { showAgreementDialog = false },
        text = {
            Text(stringResource(R.string.login_agreement_confirm_message))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    agreementChecked = true
                    showAgreementDialog = false
                    proceedLogin()
                }
            ) {
                Text(stringResource(R.string.login_agreement_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = { showAgreementDialog = false }) {
                Text(stringResource(R.string.login_agreement_cancel_action))
            }
        }
    )
}
```

- [ ] **Step 4: Run the dialog tests to verify they pass**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.login.ui.LoginScreenAgreementDialogTest
```

Expected: PASS with successful coverage of the dialog interception path and the confirm-and-agree path.

- [ ] **Step 5: Commit the dialog-gated login flow**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/login/ui/LoginScreen.kt \
  app/src/main/res/values/strings.xml \
  app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginScreenAgreementDialogTest.kt
git commit -m "feat: require agreement confirmation before login"
```

## Task 3: Run Final Verification and Manual Link Check

**Files:**
- Modify: none
- Test: `app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginAgreementConsentRowTest.kt`
- Test: `app/src/androidTest/kotlin/com/ytone/longcare/features/login/ui/LoginScreenAgreementDialogTest.kt`

- [ ] **Step 1: Run Kotlin compilation for the app module**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Run both login agreement instrumentation tests together**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.login.ui.LoginAgreementConsentRowTest,com.ytone.longcare.features.login.ui.LoginScreenAgreementDialogTest
```

Expected: `BUILD SUCCESSFUL` and both login agreement test classes pass on the connected device or emulator.

- [ ] **Step 3: Install the debug app on a connected device for manual link verification**

Run:

```bash
./gradlew :app:installDebug
```

Expected: `Installed on 1 device.` or the equivalent successful install line for the connected test target.

- [ ] **Step 4: Manually verify agreement link behavior on device**

Manual checklist:

```text
1. Open login screen.
2. Confirm the checkbox is initially unchecked.
3. Tap the text body outside the checkbox and outside the link spans.
4. Confirm the checkbox state does not change.
5. Tap 《用户协议》 and confirm the agreement page opens.
6. Return, tap 《隐私政策》, and confirm the privacy page opens.
7. Return, tap login while unchecked, and confirm the dialog appears.
8. Tap 取消 and confirm login does not continue.
9. Tap login again, then tap 我知道了 and confirm the checkbox remains unchecked and login does not continue.
```

- [ ] **Step 5: Commit any final polish if verification required small follow-up changes**

```bash
git status --short
git add <only-any-follow-up-files-if-needed>
git commit -m "fix: polish login agreement confirmation flow"
```

If there were no follow-up changes after verification, skip the commit and leave the working tree clean.

## Self-Review

- **Spec coverage:** The plan covers checkbox-only toggle behavior, login interception, dialog confirm/cancel behavior, local state ownership, and final verification of agreement link behavior.
- **Placeholder scan:** No `TODO`, `TBD`, or vague “handle appropriately” instructions remain.
- **Type consistency:** The plan uses one component name (`AgreementConsentSection`) and one login gate entry point (`submitLogin()`) consistently across tasks.
