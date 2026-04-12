# Roadmap And Open Gaps

Last verified: 2026-04-12

This document captures current delivered capability, known modernization work, and open gaps.

## 1) Delivered capabilities

The following user-facing capabilities are already implemented in current code:

- login and session-aware startup routing
- home/service entry with order lists and service detail/execution entry
- identification and face-related workflows (standard and manual capture paths)
- location tracking/reporting and location-linked workflow support
- photo capture, watermark, and upload workflow
- service countdown, end-service selection, NFC sign-in/sign-out, completion summary
- NFC test/debug entry flow
- user list, user service record, webview support screens
- startup update check and in-app update dialog flow

## 2) In-progress modernization work

Current in-progress track is architecture convergence, not missing core business flow:

- `:app` shell convergence is incomplete
  - many route-bound screens still live in `app/features/**`
- feature module extraction is partial
  - login/home/identification modules provide entries/VM/domain support, but UI route ownership remains mostly in `:app`
  - photoupload and servicecountdown modules provide support layers while route screens remain in `:app`
- documentation consolidation is in progress
  - new stable architecture docs are being introduced as primary context spine

## 3) Accepted technical debt (current)

- mixed ownership of feature UI between `:app` and `:feature:*`
- historical package layout coexistence (`app/features/**` and `feature/**`)
- navigation/type-safe route layer and feature-entry constants are not yet a single unified ownership model
- manifest and runtime component surface is broad due to existing service/receiver flows
- some legacy docs still represent historical execution plans rather than stable truth

## 4) Product/function gaps not yet implemented

No major primary-chain functional gaps are identified from current route and feature inventory.

Current "remaining work" is mostly structural and quality-oriented:

- continue modularization so route-bound UI moves out of `:app` where appropriate
- deepen regression and integration coverage on key service execution paths
- continue reducing shell-level business coupling while preserving runtime behavior

## 5) Documentation debt and context-sync debt

Current documentation debt signals:

- older plan docs contain historical intent not always aligned with current runtime ownership
- README-level summaries can drift from real module/screen ownership
- new-session recovery requires reading across multiple places unless guided

Current context-sync debt reduction direction:

- use `docs/architecture/system-overview.md` for technical truth
- use `docs/architecture/business-capability-map.md` for product capability truth
- use `docs/architecture/ui-and-screen-map.md` for screen/route truth
- use `docs/architecture/session-handoff-guide.md` for repeatable session recovery

## 6) Practical near-term roadmap

### Phase A: documentation governance stabilization (active)

- establish stable fact docs under `docs/architecture/`
- align entry docs to those stable sources

### Phase B: shell convergence

- migrate route-bound UI ownership from `:app` to `:feature:*` in small verified slices
- keep navigation contract stable during migration

### Phase C: quality depth

- add higher-value integration/smoke coverage for service execution and NFC/location/photo interactions
- keep CI quality gates and architecture guards as non-regression baseline
