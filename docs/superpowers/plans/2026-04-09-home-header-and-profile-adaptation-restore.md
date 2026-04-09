# Home Header And Profile Adaptation Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the previously accepted adaptation for the home top information block and the profile header/stats card without disturbing the current homepage entry-card behavior.

**Architecture:** Keep the restore tightly scoped to the two reverted regions. Reintroduce the previously accepted width-aware text layout in `TopHeader`, `UserInfoSection`, and `StatsCard`, and add narrow-width Compose UI tests that lock in the restored behavior. Do not touch `MainDashboardCardPrimitives.kt`, which belongs to the separate entry-card adaptation track.

**Tech Stack:** Kotlin, Jetpack Compose, Compose UI Test, JUnit4, Android Gradle app module

---

## File Structure

- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
  - Restore only the home top information block adaptation and add tags for verification.
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`
  - Restore the previously accepted profile header and stats-card adaptation, plus verification tags.
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/TopHeaderAdaptationTest.kt`
  - Verify long company-name wrapping and right-side user block stability in a compact width.
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponentsAdaptationTest.kt`
  - Verify the restored profile header layout and stable stats-card height in a compact width.

### Task 1: Add failing narrow-width UI tests for the two restored regions

**Files:**
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/TopHeaderAdaptationTest.kt`
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponentsAdaptationTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`

- [ ] **Step 1: Write the failing home-header adaptation test**

```kotlin
package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.model.User
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TopHeaderAdaptationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun long_company_name_wraps_without_colliding_with_user_block() {
        composeRule.setContent {
            LongCareTheme {
                Box(modifier = Modifier.width(328.dp)) {
                    TopHeader(
                        user = User(userName = "Mock用户", userIdentity = 1),
                        companyName = "浙江省杭州市长护智慧养老服务科技有限公司",
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_top_company_name").assertExists()
        composeRule.onNodeWithTag("home_top_user_name").assertExists()
        composeRule.onNodeWithTag("home_top_user_identity").assertExists()
        composeRule.onNodeWithTag("home_top_avatar").assertExists()

        val companyBounds = composeRule.onNodeWithTag("home_top_company_name").fetchSemanticsNode().boundsInRoot
        val avatarBounds = composeRule.onNodeWithTag("home_top_avatar").fetchSemanticsNode().boundsInRoot
        val minWrappedHeightPx = with(composeRule.density) { 32.dp.toPx() }

        assertTrue(companyBounds.height > minWrappedHeightPx)
        assertTrue(companyBounds.right < avatarBounds.left)
    }
}
```

- [ ] **Step 2: Run the home-header test and verify it fails before the restore is applied**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.TopHeaderAdaptationTest
```

Expected:

- FAIL because the required tags and restored wrapping layout are not present yet

- [ ] **Step 3: Write the failing profile restore test**

```kotlin
package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.User
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileScreenComponentsAdaptationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun profile_header_and_stats_card_restore_stable_compact_layout() {
        composeRule.setContent {
            LongCareTheme {
                Box(modifier = Modifier.width(328.dp)) {
                    Column {
                        UserInfoSection(
                            user = User(userName = "Mock用户", userIdentity = 1),
                        )
                        StatsCard(
                            actions = ProfileActions(
                                onNavigateToHaveServiceUserList = {},
                                onNavigateToNoServiceUserList = {},
                            ),
                            stats = NurseServiceTimeModel(
                                haveServiceTime = 1230,
                                haveServiceNum = 15,
                                noServiceTime = 450,
                            )
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag("profile_user_name").assertExists()
        composeRule.onNodeWithTag("profile_user_identity").assertExists()
        composeRule.onNodeWithTag("profile_user_avatar").assertExists()
        composeRule.onNodeWithTag("profile_stats_card_row").assertExists()

        val statsBounds = composeRule.onNodeWithTag("profile_stats_card_row").fetchSemanticsNode().boundsInRoot
        val minStatsHeightPx = with(composeRule.density) { 88.dp.toPx() }

        assertTrue(statsBounds.height >= minStatsHeightPx)
    }
}
```

- [ ] **Step 4: Run the profile restore test and verify it fails before the restore is applied**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.profile.ui.ProfileScreenComponentsAdaptationTest
```

Expected:

- FAIL because the required tags and restored compact layout are not present yet

- [ ] **Step 5: Commit the failing test scaffold**

```bash
git add app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/TopHeaderAdaptationTest.kt app/src/androidTest/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponentsAdaptationTest.kt
git commit -m "test(ui): define header and profile restore behavior"
```

### Task 2: Restore the home top information block adaptation

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
- Test: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/TopHeaderAdaptationTest.kt`

- [ ] **Step 1: Restore the accepted top-header layout and add verification tags**

Replace `TopHeader` with this implementation:

```kotlin
@Composable
fun TopHeader(user: User, companyName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            ImageWithAdaptiveWidth(
                drawableResId = R.drawable.app_logo_small_white,
                fixedHeight = 34.dp,
                contentDescription = stringResource(R.string.main_dashboard_logo)
            )
            if (companyName.isNotEmpty()) {
                Text(
                    text = companyName,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("home_top_company_name")
                )
            }
        }

        Column(
            modifier = Modifier.widthIn(min = 72.dp, max = 120.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = user.userName,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_top_user_name"),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Text(
                text = user.userIdentityShow(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_top_user_identity"),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        UserAvatar(
            modifier = Modifier.testTag("home_top_avatar"),
            avatarUrl = user.headUrl
        )
    }
}
```

- [ ] **Step 2: Run the home-header test and verify it passes**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.TopHeaderAdaptationTest
```

Expected:

- PASS

- [ ] **Step 3: Commit the home-header restore**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt
git commit -m "fix(home): restore top header adaptation"
```

### Task 3: Restore the profile header and stats-card adaptation

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`
- Test: `app/src/androidTest/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponentsAdaptationTest.kt`

- [ ] **Step 1: Restore the accepted `UserInfoSection` adaptation and add verification tags**

Update `UserInfoSection` to:

```kotlin
@Composable
fun UserInfoSection(user: User) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            modifier = Modifier.testTag("profile_user_avatar"),
            avatarUrl = user.headUrl
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = user.userName,
                modifier = Modifier.testTag("profile_user_name"),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = user.userIdentityShow(),
                modifier = Modifier.testTag("profile_user_identity"),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                )
            )
        }
    }
}
```

- [ ] **Step 2: Restore the accepted stats-card adaptation and add a row tag**

Update `StatsCard` and `StatItem` to:

```kotlin
@Composable
fun StatsCard(actions: ProfileActions, stats: NurseServiceTimeModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .testTag("profile_stats_card_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.haveServiceTime.toString(),
                label = "已服务工时",
                onClick = actions.onNavigateToHaveServiceUserList
            )
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFFF0F0F0)
            )
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.haveServiceNum.toString(),
                label = "服务次数"
            )
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFFF0F0F0)
            )
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.noServiceTime.toString(),
                label = "未服务工时",
                onClick = actions.onNavigateToNoServiceUserList
            )
        }
    }
}

@Composable
fun StatItem(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (onClick != null) Color(0xFF333333) else Color(0xFF666666),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (onClick != null) Color(0xFF666666) else Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

- [ ] **Step 3: Run the profile restore test and verify it passes**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.profile.ui.ProfileScreenComponentsAdaptationTest
```

Expected:

- PASS

- [ ] **Step 4: Run the shared build verification**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected:

- PASS

- [ ] **Step 5: Commit the profile restore**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt
git commit -m "fix(profile): restore header and stats adaptation"
```

## Self-Review

### Spec coverage

- Scope limited to A + B only: covered in Task 2 and Task 3
- Home top information restore: covered in Task 2
- Profile header restore: covered in Task 3 Step 1
- Profile stats-card restore: covered in Task 3 Step 2
- Preserve current entry-card implementation: enforced by file structure and no-touch scope

### Placeholder scan

- No `TODO` / `TBD`
- All steps include exact file paths, code, and commands
- Validation commands are explicit

### Type consistency

- Tags used consistently:
  - `home_top_company_name`
  - `home_top_user_name`
  - `home_top_user_identity`
  - `home_top_avatar`
  - `profile_user_name`
  - `profile_user_identity`
  - `profile_user_avatar`
  - `profile_stats_card_row`
- Test file names and target composables match the implementation steps
