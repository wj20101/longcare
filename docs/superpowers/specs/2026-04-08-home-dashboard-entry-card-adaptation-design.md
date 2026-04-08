# Home Dashboard Entry Card Adaptation Design

## Background

The home dashboard has two quick-entry cards:

- `待护理计划`
- `已服务记录`

On compact screens, these cards currently have adaptation defects:

- titles may truncate too early
- the badge can compete with title space
- earlier experimental fixes changed the UI shape in ways that no longer match the intended design

The approved constraints for this round are:

- the two cards must always remain in a single row
- the two cards must remain visually equal in height
- the titles must stay single-line
- compact screens should prioritize title readability over subtitle visibility
- subtitle may be hidden on compact screens

## Goal

Fix compact-screen adaptation for the two home dashboard entry cards while preserving the existing visual structure of the homepage.

## Non-Goals

- No single-column fallback for these two cards.
- No wording changes such as shortening `待护理计划` or `已服务记录`.
- No redesign of the banner, top header, tabs, or profile screen in this task.
- No continuous auto-scaling that makes text look unstable across widths.

## Options Considered

### Option 1: Two-line titles on compact screens

Keep the current two-card row and allow titles to wrap to two lines.

Pros:

- Easy to implement.
- Preserves subtitle visibility.

Cons:

- Breaks the approved requirement that titles must remain single-line.
- Makes card rhythm feel heavier and less aligned.

### Option 2: Single-line titles with continuous font auto-scaling

Keep the row and continuously shrink title text until it fits.

Pros:

- Preserves the two-card row.
- Can often avoid truncation.

Cons:

- Causes visible visual drift across nearby screen widths.
- Produces a less design-led, more algorithmic result.

### Option 3: Compact mode with discrete size steps and subtitle suppression

Keep the row and equal heights. On compact widths, switch the card internals into a controlled compact mode:

- title remains single-line
- title scales only across a small set of predefined sizes
- subtitle is hidden
- badge and internal spacing are slightly reduced

Pros:

- Matches the approved UI constraints.
- Keeps the existing layout shape.
- More stable than continuous auto-scaling.
- Gives the title the maximum usable width on compact devices.

Cons:

- Subtitle information is intentionally deprioritized on small screens.

## Decision

Use Option 3.

This keeps the original homepage composition intact while making compact screens deterministic and visually stable. The key idea is to adapt only the internals of the cards, not the layout structure of the cards themselves.

## Design

### 1. Revert out-of-scope experimental layout changes

Before applying the approved compact strategy, implementation should remove exploratory changes that alter the broader homepage/profile UI beyond this task’s scope.

Specifically:

- revert the single-column fallback in `DashboardGridWithImages`
- revert the continuous title auto-sizing experiment in `InfoCard`
- revert unrelated visual adaptation changes outside the two dashboard entry cards if they were introduced during this exploration round

Rationale:

- The user explicitly rejected changes that made the UI diverge from the intended design.
- This task should only adapt the two dashboard cards, not reshape surrounding sections.

### 2. Keep the card layout fixed: one row, two equal cards

The two cards remain:

- side by side
- equal width
- equal height

No responsive switch to vertical stacking is allowed for this component.

Rationale:

- The visual identity of this section depends on the two-card row.
- Structural responsiveness was explicitly rejected for this task.

### 3. Add a controlled compact mode inside `InfoCard`

For compact widths, the card should switch to an internal compact mode rather than changing page layout.

Compact mode behavior:

- title stays on one line
- title uses discrete font-size steps only, not continuous scaling
- recommended step range: `15sp -> 14sp -> 13sp`
- subtitle is hidden entirely
- icon size is slightly reduced if needed
- horizontal and vertical padding are slightly reduced if needed
- badge remains visible but should not reduce title width more than necessary

Normal mode behavior:

- current visual design remains as close as possible to the existing appearance
- subtitle remains visible

Rationale:

- Discrete steps preserve design consistency.
- Subtitle removal frees enough space to protect title readability.
- Internal compacting is less visually disruptive than layout changes.

### 4. Move badge pressure away from the title

The pending-count badge must not compete directly with the title’s layout width.

Preferred behavior:

- keep the badge visually associated with the card
- position it so the title retains the largest possible uninterrupted text width

Implementation may use:

- a corner-aligned badge overlay, or
- a reserved badge slot that does not compress the title row excessively

Rationale:

- The badge is useful, but the card title is the primary label and must win the space conflict.

### 5. Use compact mode only when necessary

Compact mode should activate from measured card width or section width rather than global device type assumptions.

The trigger should be tied to the actual card layout constraints in this section so behavior remains stable across:

- narrow phones
- split-screen widths
- future padding adjustments

Rationale:

- Local layout constraints are more reliable than broad screen buckets.

## Files Expected To Change

- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardHeaderCards.kt`
  - remove the single-column fallback and keep the two-card row stable
- `app/src/main/kotlin/com/ytone/longcare/features/maindashboard/ui/MainDashboardCardPrimitives.kt`
  - implement the approved compact-mode behavior inside `InfoCard`

Files that may need rollback-only cleanup if touched during the rejected experiments:

- `app/src/main/kotlin/com/ytone/longcare/features/profile/ui/ProfileScreenComponents.kt`

## Validation

Implementation is complete only if all of the following are true:

- `./gradlew :app:assembleDebug` passes
- On the compact-screen target:
  - `待护理计划` is fully readable on one line
  - `已服务记录` is fully readable on one line
  - the two cards remain side by side
  - the two cards remain equal in height
  - the badge does not overlap or visually crowd the title
  - subtitle may be hidden in compact mode
- On wider screens:
  - the cards still look like the original design
  - subtitle remains visible when space allows

## Risks

### Risk: compact-mode threshold is tuned too aggressively

Mitigation:

- Use the narrow screenshot scenario as the primary validation case.
- Keep the threshold tied to actual layout width rather than vague device classes.

### Risk: title still truncates at the smallest supported width

Mitigation:

- Prefer discrete size steps before truncation.
- Remove subtitle first on compact screens before shrinking below the approved minimum.

### Risk: rollback scope accidentally removes accepted improvements elsewhere

Mitigation:

- Restrict implementation to the two dashboard entry cards plus any explicit rollback of rejected experimental changes.
- Do not modify unrelated home/profile sections in the final patch.

## Follow-up

If compact adaptation issues appear in other homepage modules, handle them as separate scoped design tasks. This change is intentionally limited to the two dashboard quick-entry cards.
