# Credential Rotation Checklist (Post-History-Rewrite)

Date: 2026-02-18

## Priority P0 (immediate)

- [ ] Rotate Tencent face credentials previously exposed in history:
  - [ ] App ID
  - [ ] App Secret
  - [ ] Licence/License key
- [ ] Rotate AMAP key previously exposed in history.
- [ ] Confirm old Tencent/AMAP credentials are revoked (not just replaced).

## Signing material

- [ ] Replace Android release keystore if compromised.
- [ ] Replace signing passwords (store/key passwords).
- [ ] Re-issue and store new values in secure secret manager.

## CI/CD secret updates

- [ ] Update GitHub Environment `release` secrets:
  - [ ] `ANDROID_KEYSTORE_BASE64`
  - [ ] `RELEASE_STORE_PASSWORD`
  - [ ] `RELEASE_KEY_ALIAS`
  - [ ] `RELEASE_KEY_PASSWORD`
- [ ] Run release workflow dry run to validate signing.

## Local/dev environment updates

- [ ] Update local `~/.gradle/gradle.properties` with new `LONGCARE_RELEASE_*` values.
- [ ] Remove any temporary plaintext secret files.

## Suggested command for signing secret sync

```bash
./scripts/release/setup-signing-secrets.sh \
  --jks /absolute/path/to/new-release.jks \
  --env release \
  --copy-base64
```

## Evidence to record

- [ ] Rotation completion timestamp.
- [ ] Operator/responsible person.
- [ ] Revocation confirmation screenshot or audit log.
- [ ] Release pipeline success run URL after rotation.
