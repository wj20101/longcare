# GitHub Support Ticket Draft

Subject:

`Request purge of stale PR refs/cache objects after secret-removal force-push`

Body:

```text
Hello GitHub Support,

We performed a full history rewrite on the repository below to remove exposed secrets:
https://github.com/yyg20101/longcare

Current status:
- Branch/tag refs are clean after force-push.
- Old objects are still reachable via pull request refs (refs/pull/*), which still surface in --all mirror scans.
- Affected pull request heads include: #2 through #18.

Please purge stale pull request refs/cache objects and unreachable secret-bearing objects from backend storage for this repository.

We can provide specific old commit SHAs if needed for validation.

Thanks.
```

## Optional validation details to include

- Example old commit still reachable via PR refs before backend purge:
  - `8e73715565bcc275678ba5ef5f206751ab877475`
- Mirror scan behavior:
  - `git log --branches --tags -S'<secret>'` => clean
  - `git log --all -S'<secret>'` => still hits via `refs/pull/*`
