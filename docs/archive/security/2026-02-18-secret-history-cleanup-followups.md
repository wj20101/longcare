# Secret History Cleanup Follow-ups (2026-02-18)

## Current status

- `branches/tags` scope is sanitized for:
  - Tencent face keys (`TX_ID`, `TX_Secret`, `TX_Licence`)
  - AMAP key (`AMAP_API_KEY`)
  - historical signing passwords (`longcare^&*()`, `longcare~!@#$%`)
  - tracked `keystore.jks` history
- Remote history rewrite has been force-pushed.
- Local working tree has been re-synced to rewritten `origin/master` while preserving uncommitted work.
- Local keystore guard check passed:
  - `scripts/quality/verify_no_tracked_keystore_files.sh` => `no tracked keystore files found`

## Important caveat

- GitHub PR refs still retain old objects (`refs/pull/*`), so `git log --all` on a mirror can still see old values.
- This does **not** mean `branches/tags` are dirty; it means GitHub-side PR refs/cache cleanup is still pending.
- Affected PR refs (head): `2` to `18`.

## Remaining checklist

- [ ] Rotate all impacted third-party credentials immediately:
  - [ ] Tencent face credentials (old `TX_ID`/`TX_Secret`/`TX_Licence`)
  - [ ] AMAP key (old `AMAP_API_KEY`)
- [ ] Rotate Android signing material if any leaked keystore/password was ever valid:
  - [ ] replace keystore
  - [ ] replace signing passwords
  - [ ] update CI secrets / release environment
- [ ] Notify all collaborators to hard-reset/re-clone.
- [ ] Open GitHub Support request to clear PR refs/cache objects after force history rewrite.

## Teammate reset instructions

Run in each collaborator clone:

```bash
git fetch --all --prune
git checkout master
git reset --hard origin/master
git for-each-ref --format='delete %(refname)' refs/original | git update-ref --stdin || true
git gc --prune=now --aggressive
```

For feature branches, recreate from new `origin/master` and cherry-pick needed commits.

## GitHub Support ticket template

Subject:

`Request purge of stale PR refs/cache objects after secret-removal force-push`

Body:

```text
Hello GitHub Support,

We performed a full history rewrite on repository:
https://github.com/yyg20101/longcare

Reason: secret exposure remediation (keys removed and history force-pushed).

Current state:
- branch/tag refs are clean
- stale objects remain reachable via PR refs (refs/pull/*), causing old secrets to still appear in --all scans
- affected PR refs include #2 through #18

Please purge stale pull request refs/cache objects and related unreachable secret-bearing objects from your backend.

If you need commit IDs for validation, we can provide them.

Thank you.
```

## Post-rotation verification checklist

- [ ] Confirm old Tencent credentials are revoked and unusable.
- [ ] Confirm old AMAP key is revoked and unusable.
- [ ] Confirm CI/release uses only new secrets.
- [ ] Run secret scan on current branch:

```bash
rg -n --hidden --glob '!.git' 'TX_ID|TX_Secret|TX_Licence|AMAP_API_KEY|storePassword|keyPassword|keystore.jks' .
```

- [ ] Re-run history check for `branches/tags`:

```bash
git clone --mirror https://github.com/yyg20101/longcare.git /tmp/longcare-verify.git
cd /tmp/longcare-verify.git
git log --branches --tags -S'c21b5b5a0ff68415027171eb6a4655f9' --oneline
git log --branches --tags -S'IDAQUGBU' --oneline
git log --branches --tags -S'longcare^&*()' --oneline
git log --branches --tags --name-only --pretty=format: -- keystore.jks
```
