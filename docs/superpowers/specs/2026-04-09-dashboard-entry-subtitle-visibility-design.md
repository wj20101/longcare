# Dashboard Entry Subtitle Visibility Design

## Background

The homepage dashboard entry cards currently preserve the single-row two-column layout, but their second-line subtitle content does not display correctly on compact screens.

Affected cards:

- `待护理计划`
- `已服务记录`

The user confirmed the desired priority for this round:

- the second-line subtitle content must display normally on compact screens
- the cards should remain in one row
- the cards should remain equal in height

This task is intentionally separate from:

- the home top-header restore
- the profile restore

## Goal

Restore stable subtitle visibility for the two homepage dashboard entry cards on compact screens without changing the single-row two-column layout.

## Non-Goals

- No changes to the homepage top information block.
- No changes to the profile page.
- No switch to single-column card layout.
- No copy changes to card titles or subtitles.

## Options Considered

### Option 1: Keep dual-column layout and increase card height to preserve subtitle space

Maintain the current dual-card row, keep badge external, keep title single-line, and add enough vertical space so the subtitle can render across two lines.

Pros:

- Best matches the user’s stated priority.
- Keeps the current visual structure.
- Low risk to surrounding layout.

Cons:

- Slightly increases the visual height of the card row.

### Option 2: Keep card height fixed and shrink title more aggressively

Preserve the row and card height, but reduce title size further to free subtitle room.

Pros:

- Smaller visual height change.

Cons:

- Can make titles visually weak or inconsistent.
- Still may not guarantee stable subtitle visibility.

### Option 3: Shorten subtitle copy on compact screens

Use alternate shorter subtitle text so it fits inside the current compact card.

Pros:

- Technically simple.

Cons:

- Changes approved copy rather than fixing layout.
- Does not solve the root adaptation issue.

## Decision

Use Option 1.

The compact-mode card should keep the current dual-column structure while explicitly preserving subtitle space through vertical layout capacity rather than hiding or rewriting content.

## Design

### 1. Keep the dual-column dashboard entry layout unchanged

The two entry cards must remain:

- side by side
- equal width
- equal height

No single-column fallback is allowed in this task.

### 2. Preserve title readability while keeping subtitle visible

Compact-mode behavior should still allow title size control, but subtitle visibility becomes a required output rather than an optional one.

Compact-mode priorities become:

1. badge does not steal text width
2. title remains single-line
3. subtitle remains visible across two lines
4. card height increases as needed to support the above

### 3. Increase card vertical capacity for compact mode

The compact entry card should reserve enough height for:

- one single-line title
- two subtitle lines
- existing icon and card padding

This means compact-mode cards should no longer rely on subtitle suppression.

### 4. Keep subtitle behavior explicit in the resolver

The card layout resolver should no longer hide subtitles for compact widths. Instead, it should keep subtitle display enabled and shift adaptation responsibility toward title-size tiering and card height.

### 5. Update test expectations to reflect the approved behavior

The compact-mode dashboard test should be updated to assert:

- titles exist
- subtitles are visible
- cards remain horizontally aligned and equal in height

This replaces the earlier compact-mode assumption that subtitles should be absent.

## Files Expected To Change

- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
  - adjust compact-mode card layout behavior to preserve subtitle visibility
- `app/src/androidTest/kotlin/com/ytone/longcare/features/maindashboard/ui/DashboardGridCompactModeTest.kt`
  - update the compact-width expectations to require subtitle visibility

## Files Expected Not To Change

- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`

## Validation

Implementation is complete only if all of the following are true:

- `./gradlew :app:assembleDebug` passes
- Compact-width dashboard test expectations match the new approved behavior
- On compact screens:
  - `待护理计划` subtitle is visible
  - `已服务记录` subtitle is visible
  - both cards remain in one row
  - both cards remain equal in height

## Risks

### Risk: increased card height makes the section feel visually heavy

Mitigation:

- keep the increase minimal and targeted only to the amount needed for two subtitle lines

### Risk: title and subtitle compete for space again

Mitigation:

- keep badge external
- keep title single-line
- tune compact title size discretely rather than continuously

## Follow-up

If compact spacing still feels visually off after subtitle restoration, handle that as a small follow-up polish task rather than mixing it into this behavior restore.
