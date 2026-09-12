# Gridiron StatScout — Project Guide

Gridiron StatScout: NFL advanced-stats percentiles / player-comparison app (iOS).
XcodeGen project/scheme: `StatScout` (names kept to minimize churn), sim lease
owner `football`. Bundle id `com.jackwallner.football`, product name "Gridiron StatScout".

**Naming, as of 2026-07-29:** App Store name is **"Football Next: StatScout"** (ASC app
`6792930447`, **1.0 is live / READY_FOR_SALE** as of 2026-08-17) — chosen for ASO. In-app it is still
`PRODUCT_NAME: "Gridiron StatScout"`, home-screen `StatScout`, paid tier `StatScout+`.
ASO plan: `aso-plan.md` · `docs/astro-aso-setup.md` · `docs/localization-aso.md`.

**This repo is NOT the fastlane template canonical source** — that lives in the
baseball StatScout repo. Metadata/screenshots here are app-specific.

**ASC draft versions:** `scripts/asc-ensure-draft-version.py` treats
`ASC_DRAFT_VERSION` as *bump from here*, so passing `1.1.0` creates **1.1.1**.
And a draft cannot be deleted once any build exists for the platform (409
`STATE_ERROR`) — but an editable version's `versionString` **is** patchable, so
`PATCH /appStoreVersions/{id}` is how you land on the exact number you wanted.

**App Store reviews:** enjoyment funnel in `StatScout/Services/ReviewPromptTracker.swift`
(passive triggers: 3rd+ player profile open, Pro player comparison). feedback
`jackwallner+bb@gmail.com`.

## Backend / data pipeline (NFL)

StatScout is backed by a Supabase NFL dataset fed by a nightly pipeline (already live).

- **Supabase** is a separate account from the other apps: the Management API /
  CLI token does not reach it, so apply schema with `psql` using
  `SUPABASE_DB_PASSWORD`. Creds are in `~/.football_credentials`.
- The historical plist is the only source of past seasons in the app, and
  `backend/prune_history.py` is a manual tool, never a nightly step.
- Data source, tables, categories, refresh workflows, the Recent Form window,
  pruning, and regenerating the historical bundle are in
  `.claude/rules/backend-pipeline.md`, which loads when you read a matching file;
  AGENTS.md readers should open it directly. The duplicate-screenshot history is
  in `.claude/rules/screenshot-history.md`.
- **TestFlight upload** sources the creds first: `source ~/.football_credentials && bash scripts/testflight.sh`.

---
Shared iOS conventions (build, simulator, release scripts, ASC key, review funnel, signing, gotchas):
always-loaded global CLAUDE.md + the `ios-dev` skill.
