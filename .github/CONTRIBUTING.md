# Contributing

## Branch model

- `master` — release / CI branch
- `dev` — main development branch (source of truth)

All development happens in `dev`.

## Allowed merge direction

- ✅ `dev` → `master` (via Pull Request)
- ❌ `master` → `dev` (never)

`master` is never merged back into `dev`.

## Workflow

1. Work only on `dev`
2. Push commits to `dev`
3. Create PR: `dev` → `master`
4. Merge PR (squash or merge)
5. Continue working in `dev`

Feature branches are optional and short-lived.
Long-living feature branches are discouraged.

## Hotfixes

In rare cases:
- Create `hotfix/*` from `master`
- Merge into `master`
- Cherry-pick fix into `dev`

Do not merge `master` into `dev`.

## Forbidden actions

- No direct commits to `master`
- No `git merge master` while on `dev`
- No `reset --hard` on shared branches
