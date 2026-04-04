# Bottom Safe Area Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bottom primary action areas consistently render above the system navigation bar across NFC and the agreed set of bottom-action screens.

**Architecture:** Introduce one shared bottom safe-area container under `app/ui/components`, then migrate the identified feature bottom bars to use it or to follow the same inset calculation if their structure is too specialized. Keep business logic unchanged and limit scope to bottom visibility and spacing.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, WindowInsets/navigation bar padding APIs, Gradle app compilation, real-device screenshot validation.

---

## File Structure

- `app/src/main/kotlin/com/ytone/longcare/ui/components/BottomSafeActionContainer.kt`
  Purpose: Own reusable bottom action safe-area handling, including navigation bar inset padding, baseline spacing, and optional gradient background wrapper.

- `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt`
  Purpose: Switch the NFC bottom action area to the shared safe-area container without changing success/failure/idle behavior.

- `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownBottomActionBar.kt`
  Purpose: Preserve the existing gradient-backed bottom action area while routing spacing through the shared safe-area container.

- `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadBottomActionBar.kt`
  Purpose: Apply shared bottom inset handling to the plain bottom action area used in photo upload.

- `app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreenSupportViews.kt`
  Purpose: Apply shared bottom inset handling to the select-service action area while preserving its gradient background and dual-button layout.

- `app/src/main/kotlin/com/ytone/longcare/features/identification/ui/IdentificationScreenScaffoldSections.kt`
  Purpose: Apply shared bottom inset handling to the identification next-step button area.

- `app/src/main/kotlin/com/ytone/longcare/features/servicecomplete/ui/ServiceCompleteScreen.kt`
  Purpose: Apply shared bottom inset handling to the service-complete bottom action while preserving the current `Surface`-backed bottom bar semantics.

## Task 1: Create the Shared Bottom Safe-Area Container

**Files:**
- Create: `app/src/main/kotlin/com/ytone/longcare/ui/components/BottomSafeActionContainer.kt`

- [ ] **Step 1: Write the shared container file**

Create the file with one focused composable:

```kotlin
package com.ytone.longcare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BottomSafeActionContainer(
    modifier: Modifier = Modifier,
    horizontalPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    topPadding: PaddingValues = PaddingValues(top = 16.dp),
    extraBottomPadding: PaddingValues = PaddingValues(bottom = 16.dp),
    gradientBackground: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()

    val decoratedModifier = modifier
        .fillMaxWidth()
        .then(
            if (gradientBackground != null) {
                Modifier.background(gradientBackground)
            } else {
                Modifier
            }
        )
        .padding(horizontalPadding)
        .padding(topPadding)
        .padding(
            bottom = navigationBarPadding.calculateBottomPadding() +
                extraBottomPadding.calculateBottomPadding()
        )

    Box(modifier = decoratedModifier, content = content)
}
```

- [ ] **Step 2: Run Kotlin compile to verify the new shared component compiles**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit the shared container**

```bash
git add app/src/main/kotlin/com/ytone/longcare/ui/components/BottomSafeActionContainer.kt
git commit -m "refactor(ui): add bottom safe action container"
```

## Task 2: Migrate the NFC Bottom Bar

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt`

- [ ] **Step 1: Replace fixed bottom padding with the shared container**

Update the file to import and use `BottomSafeActionContainer`:

```kotlin
import com.ytone.longcare.ui.components.BottomSafeActionContainer
```

Replace the current `Surface -> Box(padding(...))` wrapper with:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent
) {
    BottomSafeActionContainer(
        horizontalPadding = PaddingValues(horizontal = 16.dp),
        topPadding = PaddingValues(top = 16.dp),
        extraBottomPadding = PaddingValues(bottom = 16.dp)
    ) {
        when (signInState) {
            ...
        }
    }
}
```

Keep the existing `ActionButton` and idle card behavior exactly as-is.

- [ ] **Step 2: Compile the app module again**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit the NFC migration**

```bash
git add app/src/main/kotlin/com/ytone/longcare/features/nfc/ui/NfcWorkflowBottomBar.kt
git commit -m "fix(nfc): keep bottom action above navigation bar"
```

## Task 3: Migrate Gradient-Backed Bottom Action Areas

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownBottomActionBar.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreenSupportViews.kt`

- [ ] **Step 1: Update `ServiceCountdownBottomActionBar.kt` to use the shared container with gradient**

Import the shared container:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.ytone.longcare.ui.components.BottomSafeActionContainer
```

Replace the current bottom `Box(...background(...).padding(...))` wrapper with:

```kotlin
BottomSafeActionContainer(
    modifier = Modifier.align(Alignment.BottomCenter),
    horizontalPadding = PaddingValues(horizontal = 16.dp),
    topPadding = PaddingValues(top = 0.dp),
    extraBottomPadding = PaddingValues(bottom = 16.dp),
    gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xFFF6F9FF).copy(alpha = 0.9f),
            Color(0xFFF6F9FF)
        ),
        startY = 0f,
        endY = 100f
    )
) {
    Button(...)
}
```

- [ ] **Step 2: Update `SelectServiceBottomActions` to use the shared container with the existing gradient**

Import:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.ytone.longcare.ui.components.BottomSafeActionContainer
```

Replace the outer `Box(...background(...).padding(...))` with:

```kotlin
BottomSafeActionContainer(
    modifier = modifier,
    horizontalPadding = PaddingValues(horizontal = 20.dp),
    topPadding = PaddingValues(top = 0.dp),
    extraBottomPadding = PaddingValues(bottom = 16.dp),
    gradientBackground = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color(0xFFF6F9FF).copy(alpha = 0.9f),
            Color(0xFFF6F9FF)
        ),
        startY = 0f,
        endY = 100f
    )
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ...
    }
}
```

- [ ] **Step 3: Re-run app compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit the gradient-backed migrations**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/servicecountdown/ui/ServiceCountdownBottomActionBar.kt \
  app/src/main/kotlin/com/ytone/longcare/features/selectservice/ui/SelectServiceScreenSupportViews.kt
git commit -m "fix(ui): apply bottom safe area to gradient action bars"
```

## Task 4: Migrate Plain Bottom Action Areas

**Files:**
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadBottomActionBar.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/identification/ui/IdentificationScreenScaffoldSections.kt`
- Modify: `app/src/main/kotlin/com/ytone/longcare/features/servicecomplete/ui/ServiceCompleteScreen.kt`

- [ ] **Step 1: Update `PhotoUploadBottomActionBar.kt` to use the shared container**

Import:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.ytone.longcare.ui.components.BottomSafeActionContainer
```

Replace the current `Surface -> Box(padding(...))` wrapper with:

```kotlin
Surface(modifier = Modifier.fillMaxWidth()) {
    BottomSafeActionContainer(
        horizontalPadding = PaddingValues(horizontal = 24.dp),
        topPadding = PaddingValues(top = 16.dp),
        extraBottomPadding = PaddingValues(bottom = 8.dp)
    ) {
        ConfirmAndNextButton(...)
    }
}
```

- [ ] **Step 2: Update `IdentificationBottomBar` to use the shared container**

Import:

```kotlin
import androidx.compose.foundation.layout.PaddingValues
import com.ytone.longcare.ui.components.BottomSafeActionContainer
```

Replace the current `Surface -> Box(padding(...))` wrapper with:

```kotlin
Surface(
    modifier = Modifier.fillMaxWidth(),
    color = Color.Transparent
) {
    BottomSafeActionContainer(
        horizontalPadding = PaddingValues(horizontal = 20.dp),
        topPadding = PaddingValues(top = 16.dp),
        extraBottomPadding = PaddingValues(bottom = 8.dp)
    ) {
        Button(...)
    }
}
```

- [ ] **Step 3: Update `ServiceCompleteScreen.kt` bottomBar to use the shared container**

Import:

```kotlin
import com.ytone.longcare.ui.components.BottomSafeActionContainer
```

Replace the current `bottomBar = { Surface { Box(padding(...)) { ActionButton(...) } } }` block with:

```kotlin
bottomBar = {
    Surface(modifier = Modifier.fillMaxWidth()) {
        BottomSafeActionContainer(
            horizontalPadding = 24.dp,
            topPadding = 16.dp,
            extraBottomPadding = 16.dp
        ) {
            ActionButton(
                text = "完成",
                onClick = {
                    viewModel.clearSelectedProjects(orderKey.orderId)
                    actions.onNavigateHomeAndClearStack()
                }
            )
        }
    }
}
```

Keep the current action logic exactly as-is.

- [ ] **Step 4: Re-run app compilation**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the plain-bottom migrations**

```bash
git add \
  app/src/main/kotlin/com/ytone/longcare/features/photoupload/ui/PhotoUploadBottomActionBar.kt \
  app/src/main/kotlin/com/ytone/longcare/features/identification/ui/IdentificationScreenScaffoldSections.kt \
  app/src/main/kotlin/com/ytone/longcare/features/servicecomplete/ui/ServiceCompleteScreen.kt
git commit -m "fix(ui): add bottom safe area to plain action bars"
```

## Task 5: Real-Device Verification

**Files:**
- Modify: none

- [ ] **Step 1: Install the latest debug build**

Run:

```bash
./gradlew :app:installDebug
```

Expected: `Installed on 1 device.` or equivalent successful install output.

- [ ] **Step 2: Capture NFC workflow screenshot after adaptation**

Run:

```bash
/Users/wajie/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /tmp/nfc-bottom-safe.png
```

Expected: the NFC bottom button is fully visible above the navigation bar.

- [ ] **Step 3: Navigate to one more adapted page and capture a screenshot**

Run:

```bash
/Users/wajie/Library/Android/sdk/platform-tools/adb exec-out screencap -p > /tmp/another-bottom-safe.png
```

Expected: the selected second page's bottom action area is also fully visible above the navigation bar with no obvious over-spacing. Prefer `ServiceCompleteScreen` as the second validation target because it is a plain `Scaffold(bottomBar = ...)` screen and complements the NFC verification well.

- [ ] **Step 4: Perform manual acceptance check**

Manual checklist:

```text
1. Confirm NFC bottom primary action is no longer clipped by the navigation bar.
2. Confirm the page still responds correctly when retry/success actions are used.
3. Confirm one additional adapted page also renders its bottom action area fully above the system navigation bar.
4. Confirm there is no obviously excessive empty bottom gap in the checked pages.
```

- [ ] **Step 5: Commit any follow-up fix from real-device validation**

```bash
git status --short
git add <only-follow-up-files-if-needed>
git commit -m "fix(ui): polish bottom safe area spacing"
```

If real-device validation requires no further changes, skip this step.

## Self-Review

- **Spec coverage:** The plan covers the shared container, NFC, the agreed multi-page rollout, visual preservation, compile verification, and real-device validation.
- **Placeholder scan:** No `TODO`, `TBD`, or “handle appropriately” language remains.
- **Type consistency:** The shared component is consistently named `BottomSafeActionContainer` across all tasks, and the same inset strategy is used throughout the plan.
