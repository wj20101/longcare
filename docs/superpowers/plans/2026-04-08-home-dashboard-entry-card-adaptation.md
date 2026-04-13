# Home Dashboard Entry Card Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix compact-screen adaptation for the homepage `待护理计划` and `已服务记录` cards while keeping them in one row, equal in height, and visually aligned with the original UI.

**Architecture:** Keep the section layout unchanged and move all adaptation into the card internals. Use a small deterministic layout-spec resolver with discrete title-size tiers and subtitle suppression in compact mode, then wire `InfoCard` to that resolver. Roll back rejected experimental changes before applying the approved compact behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Compose UI Test, JUnit4, Gradle Android app module

---

## File Structure

- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
  - Own the compact-mode resolver and `InfoCard` rendering updates.
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
  - Revert the single-column fallback and restore the fixed two-card row.
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`
  - Roll back unrelated profile adaptation experiments introduced during the rejected exploration.
- Create: `app/src/test/kotlin/com/ytone/longcare/features/maindashboard/ui/InfoCardLayoutSpecResolverTest.kt`
  - Unit-test the compact-mode decision logic with discrete width buckets.
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`
  - Verify the narrow-width UI behavior for titles, subtitles, and equal-height row layout.

### Task 1: Add failing tests for the approved compact-card behavior

**Files:**
- Create: `app/src/test/kotlin/com/ytone/longcare/features/maindashboard/ui/InfoCardLayoutSpecResolverTest.kt`
- Create: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`

- [ ] **Step 1: Write the failing JVM test for discrete compact-mode rules**

```kotlin
package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoCardLayoutSpecResolverTest {

    @Test
    fun compact_width_hides_subtitle_and_uses_smallest_supported_title_size() {
        val spec = resolveInfoCardLayoutSpec(cardWidth = 160.dp, hasBadge = true)

        assertEquals(13.sp, spec.titleFontSize)
        assertFalse(spec.showSubtitle)
        assertEquals(28.dp, spec.iconSize)
        assertEquals(8.dp, spec.horizontalPadding)
    }

    @Test
    fun regular_width_keeps_default_title_size_and_subtitle() {
        val spec = resolveInfoCardLayoutSpec(cardWidth = 176.dp, hasBadge = false)

        assertEquals(15.sp, spec.titleFontSize)
        assertTrue(spec.showSubtitle)
        assertEquals(32.dp, spec.iconSize)
        assertEquals(10.dp, spec.horizontalPadding)
    }

    @Test
    fun badge_presence_never_changes_the_single_row_mode() {
        val compact = resolveInfoCardLayoutSpec(cardWidth = 160.dp, hasBadge = true)
        val noBadge = resolveInfoCardLayoutSpec(cardWidth = 160.dp, hasBadge = false)

        assertEquals(compact.titleFontSize, noBadge.titleFontSize)
        assertEquals(compact.showSubtitle, noBadge.showSubtitle)
    }
}
```

- [ ] **Step 2: Run the JVM test and verify it fails because the resolver does not exist yet**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.maindashboard.ui.InfoCardLayoutSpecResolverTest"
```

Expected:

- FAIL with unresolved symbol errors for `resolveInfoCardLayoutSpec`

- [ ] **Step 3: Write the failing Compose UI test for narrow-width row behavior**

```kotlin
package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardGridCompactModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun narrow_width_keeps_cards_in_one_row_and_hides_subtitles() {
        composeRule.setContent {
            LongCareTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    DashboardGridWithImages(
                        pendingCarePlanCount = 1,
                        actions = MainDashboardActions(
                            onNavigateToCarePlansList = {},
                            onNavigateToServiceRecordsList = {},
                            onNavigateToNursingExecution = {},
                            onNavigateToService = {},
                            onNavigateToServiceCountdown = {},
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithText("待护理计划").assertExists()
        composeRule.onNodeWithText("已服务记录").assertExists()
        composeRule.onNodeWithText("你有1个护理待执行").assertDoesNotExist()
        composeRule.onNodeWithText("查看过往服务记录").assertDoesNotExist()

        val pendingBounds = composeRule.onNodeWithTag("dashboard_pending_card").fetchSemanticsNode().boundsInRoot
        val recordBounds = composeRule.onNodeWithTag("dashboard_records_card").fetchSemanticsNode().boundsInRoot

        assertEquals(pendingBounds.top, recordBounds.top, 0.5f)
        assertEquals(pendingBounds.bottom, recordBounds.bottom, 0.5f)
    }
}
```

- [ ] **Step 4: Run the UI test and verify it fails before the card implementation is updated**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.DashboardGridCompactModeTest
```

Expected:

- FAIL because the card test tags and compact-mode subtitle behavior are not implemented yet

- [ ] **Step 5: Commit the failing tests scaffold**

```bash
git add app/src/test/kotlin/com/ytone/longcare/features/maindashboard/ui/InfoCardLayoutSpecResolverTest.kt app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt
git commit -m "test(maindashboard): define compact entry card behavior"
```

### Task 2: Roll back the rejected experiments and implement the approved compact mode

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`

- [ ] **Step 1: Revert the rejected layout changes in the dashboard/profile files**

Use these exact target states:

```kotlin
// MainDashboardHeaderCards.kt
@Composable
fun DashboardGridWithImages(
    pendingCarePlanCount: Int,
    actions: MainDashboardActions
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoCard(
                modifier = Modifier.weight(1f).testTag("dashboard_pending_card"),
                iconRes = R.drawable.main_ic_plan,
                title = "待护理计划",
                subtitle = if (pendingCarePlanCount > 0) "你有${pendingCarePlanCount}个护理待执行" else "",
                badgeCount = pendingCarePlanCount,
                onClick = actions.onNavigateToCarePlansList
            )
            InfoCard(
                modifier = Modifier.weight(1f).testTag("dashboard_records_card"),
                iconRes = R.drawable.main_ic_records,
                title = "已服务记录",
                subtitle = "查看过往服务记录",
                onClick = actions.onNavigateToServiceRecordsList
            )
        }
    }
}
```

```kotlin
// ProfileScreenComponents.kt
// Remove the adaptation-only changes added during the rejected exploration:
// - restore the original fixed stats-card height
// - restore original text sizing/overflow behavior in the profile-only widgets
```

- [ ] **Step 2: Add the deterministic compact-mode resolver inside `MainDashboardCardPrimitives.kt`**

Add this helper near `InfoCard`:

```kotlin
internal data class InfoCardLayoutSpec(
    val titleFontSize: TextUnit,
    val showSubtitle: Boolean,
    val iconSize: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val badgeEndInset: Dp
)

internal fun resolveInfoCardLayoutSpec(cardWidth: Dp, hasBadge: Boolean): InfoCardLayoutSpec {
    return when {
        cardWidth <= 160.dp -> InfoCardLayoutSpec(
            titleFontSize = 13.sp,
            showSubtitle = false,
            iconSize = 28.dp,
            horizontalPadding = 8.dp,
            verticalPadding = 8.dp,
            badgeEndInset = if (hasBadge) 18.dp else 0.dp
        )
        cardWidth <= 172.dp -> InfoCardLayoutSpec(
            titleFontSize = 14.sp,
            showSubtitle = false,
            iconSize = 30.dp,
            horizontalPadding = 9.dp,
            verticalPadding = 8.dp,
            badgeEndInset = if (hasBadge) 18.dp else 0.dp
        )
        else -> InfoCardLayoutSpec(
            titleFontSize = 15.sp,
            showSubtitle = true,
            iconSize = 32.dp,
            horizontalPadding = 10.dp,
            verticalPadding = 8.dp,
            badgeEndInset = if (hasBadge) 20.dp else 0.dp
        )
    }
}
```

- [ ] **Step 3: Wire `InfoCard` to the resolver instead of using continuous auto-sizing**

Update `InfoCard` to use `BoxWithConstraints`, compute the layout spec once, and render the title with a normal `Text` using the resolved font size:

```kotlin
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    iconContentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val spec = remember(maxWidth, badgeCount) {
                resolveInfoCardLayoutSpec(cardWidth = maxWidth, hasBadge = (badgeCount ?: 0) > 0)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding)
            ) {
                if ((badgeCount ?: 0) > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = Color(0xFFFFC107)
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = spec.badgeEndInset),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(spec.iconSize)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = spec.titleFontSize,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )

                        if (spec.showSubtitle && subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                modifier = Modifier.fillMaxWidth(),
                                fontSize = 10.sp,
                                color = Color.Gray,
                                lineHeight = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.maindashboard.ui.InfoCardLayoutSpecResolverTest"
./gradlew :app:assembleDebug
```

Expected:

- The unit test passes
- Debug assembly succeeds

- [ ] **Step 5: Commit the compact-mode implementation**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt
git commit -m "fix(maindashboard): stabilize compact entry card layout"
```

### Task 3: Run final UI verification and capture the accepted validation evidence

**Files:**
- Test: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`
- Modify if needed: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`

- [ ] **Step 1: Run the narrow-width Android test after implementation**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.DashboardGridCompactModeTest
```

Expected:

- PASS
- Both cards are still in one row
- Both cards have matching top and bottom bounds
- Subtitle text is absent in compact mode

- [ ] **Step 2: If the UI test reveals a mismatch, apply only the smallest parameter correction**

Allowed correction surface:

```kotlin
// Only adjust these values if the UI test still fails:
// - compact width thresholds: 160.dp / 172.dp
// - title font steps: 15.sp / 14.sp / 13.sp
// - icon sizes: 32.dp / 30.dp / 28.dp
// - badgeEndInset: 20.dp / 18.dp
// Do not reintroduce:
// - single-column fallback
// - continuous auto-sizing
// - multi-line titles
```

- [ ] **Step 3: Re-run the final verification set**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.ytone.longcare.features.maindashboard.ui.InfoCardLayoutSpecResolverTest"
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.DashboardGridCompactModeTest
```

Expected:

- All commands PASS

- [ ] **Step 4: Commit the final verification-backed polish if any correction was needed**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt
git commit -m "test(maindashboard): verify compact entry card adaptation"
```

## Self-Review

### Spec coverage

- Revert rejected experiments: covered in Task 2 Step 1
- Keep one row and equal heights: covered in Task 2 Step 1 + Task 3 Step 1
- Discrete compact mode: covered in Task 2 Step 2
- Hide subtitle on compact screens: covered in Task 2 Step 3 + Task 3 Step 1
- Protect title from badge pressure: covered in Task 2 Step 3
- Validation on compact target: covered in Task 3

### Placeholder scan

- No `TODO` / `TBD`
- All code steps contain concrete code
- All run steps contain explicit commands and expected outcomes

### Type consistency

- `InfoCardLayoutSpec`
- `resolveInfoCardLayoutSpec`
- `DashboardGridCompactModeTest`
- `InfoCardLayoutSpecResolverTest`

Names are used consistently across tasks.
