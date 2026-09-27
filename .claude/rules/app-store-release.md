---
paths:
  - "project.yml"
  - "fastlane/**/*"
  - "scripts/asc-*.py"
  - "scripts/asc-*.sh"
  - "scripts/asc_lib.py"
  - "scripts/testflight.sh"
---

# App Store release workflow

## Current release, 2026-09-27

- App Store version 1.2.1 is `READY_FOR_SALE`.
- Version 1.2.2 is `PREPARE_FOR_SUBMISSION`, with `releaseType` set to `MANUAL`.
- TestFlight build 51 is `VALID` and attached to 1.2.2. The version has not been
  submitted for review.
- The draft has all 50 version localizations copied from the live listing. The
  new What's New text is set in en-US.

## Draft version helper

`ASC_DRAFT_VERSION` is the version to bump from, not the target version. For
example, with 1.2.1 live, setting `ASC_DRAFT_VERSION=1.2.1` creates 1.2.2 when
there is no editable draft. `scripts/asc_lib.py` reuses an editable draft and
bumps an existing live version instead of returning it.

If an editable draft's `versionString` is wrong, it can be patched with
`PATCH /appStoreVersions/{id}`. A draft cannot be deleted once a build exists
for its platform (409 `STATE_ERROR`).

## TestFlight build number

`scripts/testflight.sh` increments `CURRENT_PROJECT_VERSION`, regenerates the
XcodeGen project, archives, then uploads. After a successful upload, commit the
updated build number in both `project.yml` and `StatScout.xcodeproj` in a
separate `chore:` commit. That build-number-only push does not need another
TestFlight build.

Before editing `fastlane/metadata/`, run
`./scripts/pull-appstore-metadata.sh` and compare the pull with its timestamped
backup. Do not run the full metadata uploader when changing only one version's
What's New text.
