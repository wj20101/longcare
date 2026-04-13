# Home Header And Profile Adaptation Restore Design

## Background

Two previously accepted adaptation fixes were later rolled back during a separate homepage compact-card task:

- A: home screen top information area
- B: profile screen header information area and stats card

The user confirmed that these two areas had already looked correct before the rollback and should now be restored.

This task is intentionally separate from the homepage entry-card compact-mode work. The home entry cards should keep their current accepted behavior and are not part of this restore.

## Goal

Restore the previously accepted adaptation behavior for:

- the home screen top information block
- the profile screen user-info block and stats card

while leaving the current home dashboard entry-card adaptation unchanged.

## Non-Goals

- No changes to homepage entry cards (`待护理计划` / `已服务记录`) in this task.
- No new layout experiments.
- No single-column fallback or continuous auto-scaling.
- No redesign of banners, tabs, bottom navigation, option cards, logout button, or version display.

## Options Considered

### Option 1: Restore the previously accepted local adaptation behavior

Restore the earlier accepted layout characteristics only in the two affected areas.

Pros:

- Best matches the user’s request to “restore what was already adjusted correctly”.
- Smallest scope.
- Lowest regression risk.

Cons:

- Depends on accurately preserving the previously accepted local behavior.

### Option 2: Apply a new conservative text-only fix

Avoid restoring the previous adaptation and instead add only light `maxLines` / `ellipsis` protection.

Pros:

- Smaller diff.

Cons:

- May not restore the already-accepted visual state.
- Risks repeating the earlier under-fix.

### Option 3: Re-design both regions with a stronger new adaptive layout

Treat both regions as a fresh redesign task.

Pros:

- Maximum flexibility.

Cons:

- Contradicts the user’s request to restore, not redesign.
- High risk of “UI does not match”.

## Decision

Use Option 1.

This task should restore the previously accepted adaptation behavior in only the two confirmed regions and avoid disturbing the current accepted homepage card layout.

## Design

### 1. Scope the restore to A + B only

This restore is limited to:

- A: the home screen top information block
- B: the profile screen header information block and stats card

It must not change the current home entry-card compact-mode implementation.

Rationale:

- The user explicitly limited the restore scope to A + B.
- Mixing this with entry-card work caused confusion in the previous iteration.

### 2. Restore home top information block adaptation

The home top information block should return to the previously accepted behavior:

- the left side remains the primary flexible region
- company name can wrap across multiple lines for readability
- the right-side user name and identity remain width-bounded and protected against overflow
- the overall visual structure remains a left information group plus right user group with avatar

Key visual intent:

- company name should be readable and no longer regress into the pre-fix squeezed layout
- user identity text should not crowd or break the left-side content

Rationale:

- This was a previously accepted local adaptation and should be restored rather than re-invented.

### 3. Restore profile header information adaptation

The profile header information row should return to the previously accepted behavior:

- avatar remains fixed-size
- the adjacent text column becomes the flexible region
- user name and identity text are protected against overflow using width-aware layout behavior

Key visual intent:

- preserve the current profile page composition
- make the name/identity block stable on narrow screens without changing the page hierarchy

### 4. Restore profile stats card adaptation

The profile stats card should return to the more stable accepted version:

- avoid overly rigid height assumptions that can cause cramped text on narrow screens
- allow label text to remain readable within each equal-width stat cell
- keep the three-column structure unchanged

Key visual intent:

- numbers remain visually prominent
- labels remain readable
- the card keeps its original structure and balance

Rationale:

- The user called out this region as one of the two accepted fixes that was mistakenly reverted.

### 5. Preserve the current home entry-card implementation

No logic or layout changes should be made in:

- `MainDashboardCardPrimitives.kt`

unless required for compile safety unrelated to behavior, which is not expected here.

Rationale:

- The current request is specifically about restoring A + B while leaving the current card adaptation intact.

## Files Expected To Change

- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
  - restore only the home top information block adaptation
- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`
  - restore the profile header information and stats-card adaptation

## Files Expected Not To Change

- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreen.kt`
- other homepage/profile files outside the confirmed restore scope

## Validation

Implementation is complete only if all of the following are true:

- `./gradlew :app:assembleDebug` passes
- Home top information block:
  - company name is again readable in a stable multi-line layout
  - right-side user block no longer forces a visible regression
- Profile page:
  - user info block returns to the previously accepted adapted behavior
  - stats card returns to the previously accepted stable layout
- Homepage entry cards remain unchanged from the current accepted behavior

## Risks

### Risk: restore accidentally reopens the homepage entry-card work

Mitigation:

- Do not modify `MainDashboardCardPrimitives.kt`.
- Keep the restore limited to the top header area in `MainDashboardHeaderCards.kt`.

### Risk: restore is interpreted as a fresh redesign

Mitigation:

- Reuse the previously accepted local adaptation pattern instead of introducing a new one.
- Avoid new responsive strategies not already accepted by the user.

### Risk: “previously accepted” behavior is implemented too broadly

Mitigation:

- Change only the specific A + B regions called out by the user.
- Avoid touching neighboring sections.

## Follow-up

If additional areas were also accidentally reverted, handle them in separate scoped restore tasks rather than folding them into this one.
