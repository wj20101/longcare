# Dashboard Entry Subtitle Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore stable second-line subtitle visibility for the two homepage dashboard entry cards on compact screens while preserving the current one-row two-column layout.

**Architecture:** Keep the current card-row structure and badge placement, but shift compact-mode adaptation toward vertical capacity instead of subtitle suppression. Update the compact-mode test first so it defines the new expected behavior, then adjust the card layout resolver and card minimum height in `MainDashboardCardPrimitives.kt` to preserve subtitle visibility.

**Tech Stack:** Kotlin, Jetpack Compose, Compose UI Test, JUnit4, Android Gradle app module

---

## File Structure

- Modify: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`
  - Update compact-width expectations to require subtitle visibility.
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
  - Adjust compact card behavior to preserve two subtitle lines while keeping titles single-line and cards equal-height.

## Task 1: Update the compact-width dashboard test to the new approved behavior

**Files:**
- Modify: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`

- [ ] **Step 1: Rewrite the test expectations from hidden subtitles to visible subtitles**

Replace the current test body with this shape:

```kotlin
@Test
fun narrow_width_keeps_cards_in_one_row_and_shows_subtitles() {
    composeRule.setContent {
        LongCareTheme {
            Box(modifier = Modifier.width(328.dp)) {
                DashboardGridWithImages(
                    pendingCarePlanCount = 1,
                    actions = MainDashboardActions(
                        onNavigateToCarePlansList = {},
                        onNavigateToServiceRecordsList = {},
                        onNavigateToNursingExecution = {},
                        onNavigateToService = {},
                        onNavigateToServiceCountdown = { _, _ -> },
                    )
                )
            }
        }
    }

    composeRule.onNodeWithText("待护理计划").assertExists()
    composeRule.onNodeWithText("已服务记录").assertExists()
    composeRule.onNodeWithText("你有1个护理待执行").assertExists()
    composeRule.onNodeWithText("查看过往服务记录").assertExists()

    val pendingBounds = composeRule.onNodeWithTag("dashboard_pending_card").fetchSemanticsNode().boundsInRoot
    val recordBounds = composeRule.onNodeWithTag("dashboard_records_card").fetchSemanticsNode().boundsInRoot

    assertEquals(pendingBounds.top, recordBounds.top, 0.5f)
    assertEquals(pendingBounds.bottom, recordBounds.bottom, 0.5f)
}
```

- [ ] **Step 2: Run the compact dashboard test and verify it fails before the layout change**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.DashboardGridCompactModeTest
```

Expected:

- FAIL because the current implementation still hides subtitles on compact widths
- If the environment blocks instrumentation installation, capture and report that instead of guessing

- [ ] **Step 3: Commit the updated failing test**

```bash
git add app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt
git commit -m "test(maindashboard): expect compact subtitle visibility"
```

## Task 2: Restore subtitle visibility in compact dashboard cards

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
- Test: `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`

- [ ] **Step 1: Adjust the compact resolver so compact widths keep subtitles enabled**

Update `resolveInfoCardLayoutSpec` to preserve subtitle visibility in all width buckets:

```kotlin
internal fun resolveInfoCardLayoutSpec(cardWidth: Dp, hasBadge: Boolean): InfoCardLayoutSpec {
    return when {
        cardWidth <= InfoCardLayoutResolverDefaults.CompactThreshold -> InfoCardLayoutSpec(
            titleFontSize = InfoCardLayoutResolverDefaults.CompactTitleFontSize,
            showSubtitle = true,
            iconSize = InfoCardLayoutResolverDefaults.CompactIconSize,
            horizontalPadding = InfoCardLayoutResolverDefaults.CompactHorizontalPadding,
            verticalPadding = InfoCardLayoutResolverDefaults.VerticalPadding,
            badgeEndInset = if (hasBadge) InfoCardLayoutResolverDefaults.CompactBadgeEndInset else 0.dp
        )
        cardWidth <= InfoCardLayoutResolverDefaults.MediumThreshold -> InfoCardLayoutSpec(
            titleFontSize = InfoCardLayoutResolverDefaults.MediumTitleFontSize,
            showSubtitle = true,
            iconSize = InfoCardLayoutResolverDefaults.MediumIconSize,
            horizontalPadding = InfoCardLayoutResolverDefaults.MediumHorizontalPadding,
            verticalPadding = InfoCardLayoutResolverDefaults.VerticalPadding,
            badgeEndInset = if (hasBadge) InfoCardLayoutResolverDefaults.CompactBadgeEndInset else 0.dp
        )
        else -> InfoCardLayoutSpec(
            titleFontSize = InfoCardLayoutResolverDefaults.RegularTitleFontSize,
            showSubtitle = true,
            iconSize = InfoCardLayoutResolverDefaults.RegularIconSize,
            horizontalPadding = InfoCardLayoutResolverDefaults.RegularHorizontalPadding,
            verticalPadding = InfoCardLayoutResolverDefaults.VerticalPadding,
            badgeEndInset = if (hasBadge) InfoCardLayoutResolverDefaults.RegularBadgeEndInset else 0.dp
        )
    }
}
```

- [ ] **Step 2: Increase card vertical capacity just enough to hold title + two subtitle lines**

Introduce a named minimum height constant and apply it to the card:

```kotlin
private object InfoCardLayoutResolverDefaults {
    val CompactThreshold = 160.dp
    val MediumThreshold = 172.dp

    val CompactTitleFontSize = 13.sp
    val MediumTitleFontSize = 14.sp
    val RegularTitleFontSize = 15.sp

    val CompactIconSize = 28.dp
    val MediumIconSize = 30.dp
    val RegularIconSize = 32.dp

    val CompactHorizontalPadding = 8.dp
    val MediumHorizontalPadding = 9.dp
    val RegularHorizontalPadding = 10.dp

    val VerticalPadding = 8.dp
    val CompactBadgeEndInset = 18.dp
    val RegularBadgeEndInset = 20.dp
    val CardMinHeight = 92.dp
}
```

Then update the card modifier:

```kotlin
Card(
    onClick = { onClick?.invoke() },
    modifier = modifier.heightIn(min = InfoCardLayoutResolverDefaults.CardMinHeight),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
)
```

- [ ] **Step 3: Keep title single-line and subtitle explicitly two-line friendly**

Keep title behavior:

```kotlin
Text(
    text = title,
    fontWeight = FontWeight.Bold,
    fontSize = spec.titleFontSize,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.fillMaxWidth()
)
```

Keep subtitle rendering explicit and always enabled through the resolver:

```kotlin
if (spec.showSubtitle && subtitle.isNotBlank()) {
    Text(
        text = subtitle,
        modifier = Modifier.fillMaxWidth(),
        fontSize = 10.sp,
        color = Color.Gray,
        lineHeight = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        )
    )
}
```

- [ ] **Step 4: Run verification and confirm the compact subtitle behavior**

Run:

```bash
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.ytone.longcare.features.maindashboard.ui.DashboardGridCompactModeTest
```

Expected:

- `:app:assembleDebug` passes
- The dashboard compact test passes
- If instrumentation is blocked by device policy, report the exact blocker

- [ ] **Step 5: Commit the compact subtitle visibility restore**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt
git commit -m "fix(maindashboard): restore compact subtitle visibility"
```

## Self-Review

### Spec coverage

- Preserve dual-column layout: covered by touching only card internals, not row structure
- Keep subtitle visible on compact screens: covered in Task 2 Step 1 and Step 3
- Increase card vertical capacity: covered in Task 2 Step 2
- Update compact dashboard test expectations: covered in Task 1
- Keep top header/profile unchanged: enforced by file scope

### Placeholder scan

- No `TODO` / `TBD`
- All steps contain exact file paths, code, and commands
- Validation commands are explicit

### Type consistency

- `DashboardGridCompactModeTest`
- `InfoCardLayoutSpec`
- `resolveInfoCardLayoutSpec`
- `InfoCardLayoutResolverDefaults.CardMinHeight`

Names are used consistently across all tasks.
