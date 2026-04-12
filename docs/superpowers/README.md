# Superpowers Docs Index

## 说明

`docs/superpowers` 用来存放按主题组织的设计文档、实现计划和验收文档。

推荐使用方式：

1. 先读 `design`，理解背景、目标和边界。
2. 再读 `plan`，按步骤执行实现。
3. 如果该主题有 `acceptance`，最后用它做验收闭环。

目录约定：

- [`specs/`](specs) 存放设计文档，以及少量验收型文档
- [`plans/`](plans) 存放实现计划和任务拆解

## 当前文档总览

### 1. 认证与会话

#### Login agreement confirmation

- Design: [`specs/2026-04-02-login-agreement-confirmation-design.md`](specs/2026-04-02-login-agreement-confirmation-design.md)
- Plan: [`plans/2026-04-02-login-agreement-confirmation.md`](plans/2026-04-02-login-agreement-confirmation.md)

#### Face cache clear on logout

- Design: [`specs/2026-04-04-face-cache-clear-on-logout-design.md`](specs/2026-04-04-face-cache-clear-on-logout-design.md)
- Plan: [`plans/2026-04-04-face-cache-clear-on-logout.md`](plans/2026-04-04-face-cache-clear-on-logout.md)

### 2. 页面与布局适配

#### Bottom safe area adaptation

- Design: [`specs/2026-04-03-bottom-safe-area-adaptation-design.md`](specs/2026-04-03-bottom-safe-area-adaptation-design.md)
- Plan: [`plans/2026-04-03-bottom-safe-area-adaptation.md`](plans/2026-04-03-bottom-safe-area-adaptation.md)

#### Home dashboard entry card adaptation

- Design: [`specs/2026-04-08-home-dashboard-entry-card-adaptation-design.md`](specs/2026-04-08-home-dashboard-entry-card-adaptation-design.md)
- Plan: [`plans/2026-04-08-home-dashboard-entry-card-adaptation.md`](plans/2026-04-08-home-dashboard-entry-card-adaptation.md)

#### Dashboard entry subtitle visibility

- Design: [`specs/2026-04-09-dashboard-entry-subtitle-visibility-design.md`](specs/2026-04-09-dashboard-entry-subtitle-visibility-design.md)
- Plan: [`plans/2026-04-09-dashboard-entry-subtitle-visibility.md`](plans/2026-04-09-dashboard-entry-subtitle-visibility.md)

#### Home header and profile adaptation restore

- Design: [`specs/2026-04-09-home-header-and-profile-adaptation-restore-design.md`](specs/2026-04-09-home-header-and-profile-adaptation-restore-design.md)
- Plan: [`plans/2026-04-09-home-header-and-profile-adaptation-restore.md`](plans/2026-04-09-home-header-and-profile-adaptation-restore.md)

### 3. NFC / RFID / R65C / HID

#### NFC error dialog

- Design: [`specs/2026-04-03-nfc-error-dialog-design.md`](specs/2026-04-03-nfc-error-dialog-design.md)
- Plan: [`plans/2026-04-03-nfc-error-dialog.md`](plans/2026-04-03-nfc-error-dialog.md)

#### Type-C RFID fallback

- Design: [`specs/2026-04-05-type-c-rfid-fallback-design.md`](specs/2026-04-05-type-c-rfid-fallback-design.md)
- Plan: [`plans/2026-04-05-type-c-rfid-fallback.md`](plans/2026-04-05-type-c-rfid-fallback.md)

#### Type-C USB host test panel

- Design: [`specs/2026-04-06-type-c-usb-host-test-panel-design.md`](specs/2026-04-06-type-c-usb-host-test-panel-design.md)
- Plan: [`plans/2026-04-06-type-c-usb-host-test-panel.md`](plans/2026-04-06-type-c-usb-host-test-panel.md)

#### R65C HID keyboard test panel

- Design: [`specs/2026-04-07-r65c-hid-keyboard-test-panel-design.md`](specs/2026-04-07-r65c-hid-keyboard-test-panel-design.md)
- Plan: [`plans/2026-04-07-r65c-hid-keyboard-test-panel.md`](plans/2026-04-07-r65c-hid-keyboard-test-panel.md)

#### R65C raw HID output validation

- Design: [`specs/2026-04-09-r65c-raw-hid-output-validation-design.md`](specs/2026-04-09-r65c-raw-hid-output-validation-design.md)
- Plan: [`plans/2026-04-09-r65c-raw-hid-output-validation.md`](plans/2026-04-09-r65c-raw-hid-output-validation.md)
- Acceptance: [`specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md`](specs/2026-04-10-r65c-raw-hid-output-validation-acceptance.md)

#### R65C business fallback integration

- Design only: [`specs/2026-04-10-r65c-business-fallback-integration-design.md`](specs/2026-04-10-r65c-business-fallback-integration-design.md)

### 4. CI/CD 与质量治理

#### CI health issue 43 follow-up

- Design: [`specs/2026-04-04-ci-health-issue-43-follow-up-design.md`](specs/2026-04-04-ci-health-issue-43-follow-up-design.md)
- Plan: [`plans/2026-04-04-ci-health-issue-43-follow-up.md`](plans/2026-04-04-ci-health-issue-43-follow-up.md)

#### CI/CD governance and dev preflight

- Design: [`specs/2026-04-04-cicd-governance-and-dev-preflight-design.md`](specs/2026-04-04-cicd-governance-and-dev-preflight-design.md)
- Plan: [`plans/2026-04-04-cicd-governance-and-dev-preflight.md`](plans/2026-04-04-cicd-governance-and-dev-preflight.md)

#### Release quality gate legacy feature freeze fix

- Design: [`specs/2026-04-04-release-quality-gate-legacy-feature-freeze-fix-design.md`](specs/2026-04-04-release-quality-gate-legacy-feature-freeze-fix-design.md)
- Plan: [`plans/2026-04-04-release-quality-gate-legacy-feature-freeze-fix.md`](plans/2026-04-04-release-quality-gate-legacy-feature-freeze-fix.md)

#### Close CI health issue 43

- Design: [`specs/2026-04-05-close-ci-health-issue-43-design.md`](specs/2026-04-05-close-ci-health-issue-43-design.md)
- Plan: [`plans/2026-04-05-close-ci-health-issue-43.md`](plans/2026-04-05-close-ci-health-issue-43.md)

#### Lint waiver drift cleanup

- Design: [`specs/2026-04-05-lint-waiver-drift-cleanup-design.md`](specs/2026-04-05-lint-waiver-drift-cleanup-design.md)
- Plan: [`plans/2026-04-05-lint-waiver-drift-cleanup.md`](plans/2026-04-05-lint-waiver-drift-cleanup.md)

#### CI/CD governance hardening

- Design: [`specs/2026-04-10-ci-cd-governance-hardening-design.md`](specs/2026-04-10-ci-cd-governance-hardening-design.md)
- Plan: [`plans/2026-04-10-ci-cd-governance-hardening.md`](plans/2026-04-10-ci-cd-governance-hardening.md)

### 5. 架构探索

#### Architecture boundary RG fallback

- Design only: [`specs/2026-04-05-architecture-boundary-rg-fallback-design.md`](specs/2026-04-05-architecture-boundary-rg-fallback-design.md)

## 快速定位

如果你已经知道自己要找哪类内容，可以按下面的顺序查：

1. 业务功能设计优先看 `specs/`
2. 具体落地步骤优先看 `plans/`
3. 设备验证或改造验收优先看带 `acceptance` 的文档
4. 与 R65C、RFID、USB Host、HID 相关内容优先看 `NFC / RFID / R65C / HID`
5. 与流水线、lint、质量门禁相关内容优先看 `CI/CD 与质量治理`

## 维护建议

新增一组完整文档时，建议同步维护这里：

1. 在对应主题下新增条目
2. 标明该主题是 `Design only`、`Design + Plan` 还是 `Design + Plan + Acceptance`
3. 如果是新的领域主题，优先新增一级分组，再补文档链接
