# Team Notice: Repository History Rewritten (Action Required)

Date: 2026-02-18
Repository: `https://github.com/yyg20101/longcare.git`

## Why this is required

Repository history was force-rewritten to remove previously committed sensitive data.
All collaborators must re-sync to avoid reintroducing old commits.

## Required action (for everyone)

Run the following in your local clone:

```bash
git fetch --all --prune
git checkout master
git reset --hard origin/master
git for-each-ref --format='delete %(refname)' refs/original | git update-ref --stdin || true
git gc --prune=now --aggressive
```

## If you have local uncommitted changes

Use this safer flow:

```bash
git stash push -u -m "pre-history-resync"
git fetch --all --prune
git checkout master
git reset --hard origin/master
git stash pop
```

Resolve conflicts manually if prompted.

## Feature branch handling

- Do not keep old feature branches based on pre-rewrite commits.
- Recreate from new `origin/master` and cherry-pick only needed clean commits.

## Verification

```bash
git rev-list --left-right --count HEAD...origin/master
```

Expected output:

`0    0`
