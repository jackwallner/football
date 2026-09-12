# Technical pipeline audit: event-aware NFL refresh cadence

Audit date: 2026-09-12

Repository: `/Users/jackwallner/football`

Scope: read-only review of the current NFL data pipeline, upstream publication timing, freshness bottlenecks, idempotency, failure behavior, correctness, operational cost, and a recommended event-aware refresh architecture.

No application or workflow implementation is included in this audit.

## Executive finding

The current system is a once-daily, best-effort refresh. It is not event-aware:

1. GitHub Actions starts one job at `09:00 UTC` in `.github/workflows/nightly-statcast.yml:L3-L10`.
2. The job runs tests, rebuilds snapshots, ingests game logs, and rebuilds Recent Form in sequence.
3. The job does not check whether nflverse has published a new source asset before doing work.
4. The app only fetches new data at launch, foreground, or pull-to-refresh. There is no push or guaranteed background refresh.

The source itself is normally available about one to three hours after a game, not immediately at the final whistle. The measured 2025 source lag was a median of 2.11 hours for all games and 2.08 hours for regular-season games. The first two observed 2026 games were published to the `stats_player` release 1h17m and 2h45m after the final play.

The largest practical gap is therefore not the time spent running Python. It is that the repository waits for a fixed daily run, sometimes starts before the source is ready, and has no source-fingerprint or coverage gate. On 2026-09-12, the repository run started at `12:27 UTC`, while the upstream `stats_player` asset was updated at about `12:52 UTC`. That run could not include the latest source publication.

Recommended direction:

- Define freshness from upstream source publication, with a separate game-end expectation.
- Add a cheap source probe during active NFL windows and a daily safety probe.
- Run the full pipeline only when the source fingerprint changes or a manual force is requested.
- Reprocess an overlap window so late corrections are captured.
- Stage and validate snapshots, game logs, and Recent Form, then publish them atomically.
- Keep the last known-good dataset live when the source or a downstream stage is incomplete.
- Use GitHub Actions as the primary runner. The repository is public, so standard GitHub-hosted Actions usage is free under GitHub's current billing rules. A MacBook is not required for cost control.

## Audit scope and assumptions

The audit examined:

- `.github/workflows/nightly-statcast.yml`
- `backend/ingest.py`
- `backend/ingest_game_logs.py`
- `backend/rollup_recent_form.py`
- `backend/README.md`
- `.claude/rules/backend-pipeline.md`
- `supabase/schema.sql` and the Recent Form migration
- `backend/tests/test_ingest.py`
- `backend/tests/test_game_logs.py`
- `backend/tests/test_rollup_recent_form.py`
- the iOS fetch, cache, view-model, and settings paths that consume the data
- nflverse release metadata and the scheduled nflverse PBP workflow
- GitHub Actions run metadata and logs for the 2025 season and the first 2026 games

Assumptions used in the timing analysis:

- “Last season” means the 2025 NFL season.
- “This season” means 2026, based on the current date and the repository's season resolver.
- Times in the evidence tables are UTC.
- A PBP game's final event was approximated by the latest parsed `time_of_day` for that `game_id`.
- For 2025, old workflow logs are expired. The first successful source workflow's `updatedAt` is treated as an upper bound for publication completion, not an exact asset upload timestamp.
- The first successful source update after a game is evidence of availability, not proof that nflverse never made an earlier partial or corrected asset available.
- The desired product behavior is “show the latest trustworthy completed data soon after the source publishes it,” not “guarantee final whistle plus a fixed number of minutes.” The source controls the lower bound.

## Measured freshness evidence

### Source publication behavior

The nflverse data README states that raw JSON generally appears one to two hours after each game. It also describes daily PBP and player-stat updates around `09:00 UTC`, daily NGS updates around `07:00 UTC`, and several PFR advanced-stat polls per day. The current nflverse PBP workflow has additional schedules around Thursday night, Sunday afternoon, late Sunday, Sunday night, and Monday night games.

References:

- [nflverse data refresh notes](https://github.com/nflverse/nflverse-data/blob/main/README.Rmd)
- [nflverse scheduled PBP and stats workflow](https://github.com/nflverse/nflverse-pbp/blob/master/.github/workflows/update_data.yaml)

### First two observed 2026 games

| Game | Final PBP event | First observed successful `stats_player` upload | Approximate lag | Source run |
| --- | --- | --- | --- | --- |
| `2026_01_NE_SEA` | 2026-09-10 03:24:42 | 2026-09-10 04:41:54 | 1h17m | [34437791119](https://github.com/nflverse/nflverse-pbp/actions/runs/34437791119) |
| `2026_01_SF_LA` | 2026-09-11 03:23:53 | 2026-09-11 06:08:42 | 2h45m | [34568386129](https://github.com/nflverse/nflverse-pbp/actions/runs/34568386129) |

The first run was manually dispatched and completed shortly after the game. The second was also a manual run and completed later. These observations support a post-game source-readiness window of roughly two to three hours as a normal planning target, with retries needed for outliers.

### 2025 full-season timing audit

The 2025 PBP file contained 285 games, including 272 regular-season games and 13 postseason games. For each game, the analysis selected the first successful nflverse source workflow created after the final PBP event.

| Population | Games | Median lag | P90 lag | P95 lag | P99 lag | Maximum lag |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| All games | 285 | 2.11h | 3.32h | 5.79h | 10.91h | 17.23h |
| Regular season | 272 | 2.08h | 2.84h | not calculated | not calculated | 15.96h |
| Wild Card | 6 | 1.91h | 15.35h | not calculated | not calculated | 17.23h |
| Divisional | 4 | 4.78h | 8.53h | not calculated | not calculated | 9.45h |
| Conference | 2 | 3.31h | 3.32h | not calculated | not calculated | 3.32h |
| Super Bowl | 1 | 3.56h | not applicable | not applicable | not applicable | 3.56h |

Regular-season service levels from the same analysis:

- 133 of 272 games, 48.9%, were available within two hours.
- 249 of 272 games, 91.5%, were available within three hours.
- 253 of 272 games, 93.0%, were available within four hours.
- 263 of 272 games, 96.7%, were available within six hours.
- 265 of 272 games, 97.4%, were available within eight hours.
- 271 of 272 games, 99.6%, were available within 12 hours.
- All 272 regular-season games were available within 24 hours.

Regular-season weekly detail:

| Week | Games | Median lag | P90 lag | Maximum lag |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 16 | 2.44h | 2.81h | 6.26h |
| 2 | 16 | 2.43h | 2.66h | 3.83h |
| 3 | 16 | 2.55h | 2.68h | 2.80h |
| 4 | 16 | 2.55h | 3.24h | 5.74h |
| 5 | 14 | 2.42h | 2.70h | 5.87h |
| 6 | 15 | 2.62h | 3.28h | 6.01h |
| 7 | 15 | 2.54h | 3.29h | 5.75h |
| 8 | 13 | 2.61h | 2.81h | 2.95h |
| 9 | 14 | 1.55h | 1.95h | 2.78h |
| 10 | 14 | 1.53h | 1.72h | 4.73h |
| 11 | 15 | 1.50h | 1.78h | 4.92h |
| 12 | 14 | 1.44h | 1.81h | 1.99h |
| 13 | 16 | 1.75h | 6.72h | 10.43h |
| 14 | 14 | 1.56h | 2.11h | 15.96h |
| 15 | 16 | 1.52h | 1.79h | 2.09h |
| 16 | 16 | 1.76h | 3.48h | 8.48h |
| 17 | 16 | 1.83h | 7.19h | 8.97h |
| 18 | 16 | 1.86h | 3.96h | 9.20h |

The source workflow started at these UTC hours for the first successful update associated with games:

| Start hour UTC | Games observed |
| ---: | ---: |
| 22 | 138 |
| 01 | 59 |
| 02 | 11 |
| 05 | 63 |
| 06 | 1 |
| 09 | 8 |
| 10 | 2 |
| 17 | 2 |
| 20 | 1 |

This distribution shows why a single daily repository run is a poor match. Most source updates happened in several post-game windows, not only at the daily `09:00 UTC` run.

Representative outliers and fast paths:

| Game | Result | Evidence |
| --- | --- | --- |
| `2025_14_DAL_DET` | 15.96h lag | Two source attempts failed around 05:42 and 09:20 UTC. A later manual recovery succeeded around 20:34 UTC. |
| `2025_19_LA_CAR` | 17.23h lag | A source failure and manual recovery delayed the successful publication until about 18:02 UTC. |
| `2025_19_GB_CHI` | 13.46h lag | Same recovery period as the Wild Card outlier above. |
| `2025_07_HOU_SEA` | 0.42h lag | Source workflow started about nine minutes after the final event and published within the same run. |

The 2025 failures demonstrate that backups are necessary even when the normal source cadence is predictable. The 2026 source logs also show failures, including an upstream shell condition rendered as `elif [-z "$SEASON_REBUILD"] ||`, which produced `[-z: command not found`. This is an external dependency issue and must be verified with nflverse before depending on its schedule.

### Repository workflow timing and race evidence

The repository's scheduled workflow was observed starting at approximately `13:03:38 UTC` on 2026-09-11 and `12:27:00 UTC` on 2026-09-12, despite the configured `09:00 UTC` cron. GitHub schedule dispatch is best effort, so the configured time is not an execution-time guarantee.

On 2026-09-12:

- Repository run [34693700098](https://github.com/jackwallner/football/actions/runs/34693700098) started at `12:27 UTC` and succeeded before the upstream asset update at about `12:52 UTC`.
- Upstream run [34694692679](https://github.com/nflverse/nflverse-pbp/actions/runs/34694692679) published the current `stats_player` asset at about `12:52 UTC`.

On 2026-09-11, the repository run started around `13:03 UTC`, about 6h55m after the first observed source publication at about `06:08 UTC`. This is the user-visible delay caused by the current schedule.

## Actual current flow

### Upstream to Supabase

```text
nflverse release assets
    -> nflreadpy loaders
    -> GitHub Actions tests
    -> backend/ingest.py
       -> player_snapshots
    -> backend/ingest_game_logs.py
       -> player_game_logs
    -> backend/rollup_recent_form.py
       -> player_recent_form
    -> Supabase REST reads
```

The source and output responsibilities are documented in `backend/README.md:L3-L7` and `.claude/rules/backend-pipeline.md:L17-L23`.

### Workflow sequence

`.github/workflows/nightly-statcast.yml:L3-L10` defines one daily schedule and manual dispatch. The single job at `L35-L45` has a 180-minute timeout.

The job:

1. Checks out the repository and installs `backend/requirements.txt` at `L47-L58`.
2. Runs the backend tests at `L60-L61`.
3. Runs `python backend/ingest.py --season-type all` at `L63-L71`.
4. Optionally backfills seasons at `L76-L99`.
5. Optionally rebuilds the all-time rollup at `L101-L109`.
6. Runs incremental or manually requested full game-log ingestion at `L111-L126`.
7. Rebuilds Recent Form at `L128-L136`.
8. Uploads failure artifacts and creates a GitHub issue at `L151-L173`.

There is no source-readiness gate, source version, content fingerprint, run manifest, concurrency group, retry policy, or success marker in this workflow.

### Snapshot ingestion

`backend/ingest.py` describes a six-stage aggregate pipeline at the module docstring and builds season aggregates in `L987-L1029`:

- weekly player stats via `nfl.load_player_stats`
- derived rates and aggregate metrics
- NGS passing, rushing, and receiving data
- PFR advanced defense for regular season only
- headshots and player identity data
- percentile ranking and snapshot row construction

The main loop at `L1037-L1150` processes REG and POST for `--season-type all`. It upserts snapshots in batches of 150 using the primary key at `L1090-L1102`, then deletes rows not in the newly built set at `L1111-L1137` when a row-count floor is met.

Important operational detail: REG and POST are processed separately, and `build_agg_for_season` loads weekly stats, NGS, PFR, and headshots for each phase. The event path would repeat substantial source work unless loaders are cached or the pipeline is reorganized.

### Game-log ingestion

`backend/ingest_game_logs.py:L315-L362` loads the current season's weekly stats and schedule, optionally loads NGS weekly data, builds rows, applies the incremental filter, upserts rows, and performs full-mode cleanup.

The incremental watermark is the maximum `game_date` already stored, fetched at `L257-L268`. The filter at `L335-L341` keeps rows on or after that date so the latest day is retried.

The table key is `(player_id, season, season_type, game_date, player_type)`, used by `_upsert` at `L271-L284`. The stored row does not include `game_id`.

### Recent Form

`backend/rollup_recent_form.py:L229-L295` finds a global maximum week per season and phase, then computes each player's 3, 5, and 8-week windows. It reads all game logs at `L298-L322` and resolves rows to snapshot players at `L325-L358`.

At `L428-L434`, it deletes all Recent Form rows for the season and then upserts the rebuilt rows. The delete and rebuild are not one transaction. If the rebuild fails, the current table can be empty or partial.

If the table is missing, `_table_exists` intentionally warns and returns successfully at `L379-L411`. This can make the overall workflow green while a user-facing feature is absent.

### iOS consumption

The app fetches current snapshots, game logs, Recent Form, and coverage through `StatScout/Services/StatcastAPI.swift`. Coverage is derived from the maximum database `end_week` and `as_of` at `StatcastAPI.swift:L222-L279`.

`DashboardViewModel.load()` at `StatScout/ViewModels/DashboardViewModel.swift:L764-L853` loads disk cache first, then fetches current data from Supabase. It has a useful completeness validator and preserves a complete cached dataset if the remote response is incomplete.

Automatic fetches happen on app task and foreground notification in `StatScout/StatScoutApp.swift:L108-L116`. Manual pull-to-refresh is wired in `DashboardView.swift:L40-L42` and `TeamsView.swift:L117-L119`.

The settings screen exposes “Games Through” and “Last Refreshed” at `StatScout/Views/SettingsView.swift:L157-L193`, but the product copy still describes a nightly refresh. The app has no backend push signal and no reliable background task that guarantees a refresh while the app is closed.

## Dependency and freshness bottleneck audit

### P0 or P1 risks

| Risk | Severity | Evidence | Effect |
| --- | --- | --- | --- |
| Upstream source failures and current shell syntax defect | P0/P1 external | nflverse workflow logs and `update_data.yaml` | A scheduled source update can fail, turning a normal post-game window into a many-hour delay. |
| Repository job can run before source publication | P1 | 2026-09-12 run ordering | The daily job can complete successfully against stale data, with no retry after the source arrives. |
| No source-readiness or coverage validation | P1 | Workflow `L63-L71`; ingest main `L1065-L1146` | A non-empty but incomplete source can overwrite current rows or advance Recent Form. |
| Recent Form delete before rebuild | P1 | `rollup_recent_form.py:L428-L434` | A failed rollup can leave no or partial Recent Form data. |
| Date-only incremental watermark | P1 | `ingest_game_logs.py:L257-L268`, `L335-L341` | Corrections to a prior date are skipped after the max stored date. |
| No serialized workflow execution | P1 | No `concurrency` block in workflow | Manual and scheduled runs can overlap and interleave writes. |
| Multi-stage live writes are not atomic | P1 | Snapshot batch upserts and orphan deletion at `ingest.py:L1090-L1137`; game-log writes at `L271-L284` | A failed run can leave a mixture of old and new snapshots, logs, and rollups. |

### P2 risks and maintainability concerns

| Risk | Evidence | Effect |
| --- | --- | --- |
| Duplicate source loading for REG and POST | `ingest.py:L1002-L1027`, called once per phase at `L1065-L1070` | Longer runs and more upstream requests during the event window. |
| NGS failure degrades silently | `ingest_game_logs.py:L287-L312` | Rows can be written without enrichment, with no degraded status for the app or operator. |
| Missing Recent Form table is a successful skip | `rollup_recent_form.py:L379-L411` | Monitoring does not distinguish “feature unavailable” from “refresh healthy.” |
| Row `updated_at` is a write time, not a source version | Snapshot construction and rollup row construction | The app cannot tell whether a row is fresh, reprocessed, or merely touched by a retry. |
| No game-level identity in `player_game_logs` | `supabase/schema.sql:L39-L70`; `build_game_log_rows` | Exact game completeness, late replacement, and deletion reconciliation are difficult. |
| Global Recent Form anchor can move on partial data | `rollup_recent_form.py:L231-L237` | A partially loaded latest week can become the anchor for every player. |
| Dead compatibility branch in routing | `rollup_recent_form.py:L349-L358` | The integer membership check is incompatible with the tuple set and is never the effective branch. |
| Dependencies are not fully pinned | `backend/requirements.txt` | A source or library change can alter a scheduled run without a repository change. |
| Lenient mixed-row app decoding | `StatcastAPI.swift:L281-L325` | Some invalid rows can be silently dropped from a non-empty response. |
| Recent Form session cache | `DashboardViewModel.swift:L321-L389`, `L426-L486` | A user can see newly refreshed snapshots while seeing an older Recent Form result until the cache is invalidated. |

## Idempotency and state behavior

| Component | Current repeat behavior | Idempotency assessment |
| --- | --- | --- |
| `player_snapshots` | Upsert on `(id, season, season_type)` | Repeatable for the same input, but `updated_at` changes and orphan deletion is separate. A partial input can delete valid rows. |
| `player_game_logs` | Upsert on `(player_id, season, season_type, game_date, player_type)` | Repeatable for the same key and input. It cannot remove a source row that disappeared during an incremental run. Corrections older than the max date are missed. |
| `player_recent_form` | Delete season, then upsert deterministic primary keys | Not failure-safe. A retry usually reconstructs the table, but a reader can observe empty or mixed state between operations. |
| Workflow rerun | Repeats all writes from current source | Mostly convergent on success, but no source fingerprint prevents redundant work, and concurrent runs can interleave. |
| Full game-log cleanup | Deletes rows with `updated_at` older than the current run | Safe only if the full source read is complete. If source data is partial, valid rows can be deleted. |

The current design is therefore “eventually convergent if every run completes against a complete source and runs serially.” It is not robustly idempotent under partial input, late corrections, concurrent runs, or stage failure.

## Failure modes and expected current behavior

### Upstream unavailable or not ready

`nflreadpy` can receive a 404 when the current `stats_player_week_{season}.parquet` asset is not yet published. This happened in current repository runs after the test suite passed. The workflow fails before the downstream writes. The app should continue to serve cached or previously published data, which is preferable to publishing an empty replacement, but there is no explicit status record telling the app or operator why the data is stale.

### Source asset exists but is incomplete

The current code only checks that aggregate output is non-empty and applies a low row-count floor before pruning. It does not validate the expected completed games, expected week, expected team coverage, source timestamp, duplicate keys, or required metric completeness. A partial source can therefore be treated as valid.

### NGS or PFR enrichment fails

NGS weekly game-log loading catches exceptions and continues without NGS at `ingest_game_logs.py:L287-L312`. Snapshot NGS and PFR paths are not represented as a separate readiness state. A run can therefore publish a mixture of core and enriched metrics, and the app has no indication that the result is degraded.

### Supabase write fails mid-run

Batch upserts are performed directly against live tables. A failure after several batches leaves some rows from the new run and the remainder from the prior run. Snapshot orphan deletion can then further change the live set. Recent Form has an even more severe delete-first failure mode.

### Overlapping runs

There is no workflow concurrency group. A scheduled run, manual run, or a retry can overlap. The two runs can have different source reads and `updated_at` values. The final state depends on batch ordering rather than a deliberate source version.

### Missing or stale app data

The app fetches on foreground and manual refresh, not at the moment the backend changes. The current cache fallback is good protection against empty or incomplete remote responses, but it can preserve stale data without a source status or explicit “last checked” distinction.

### Monitoring noise and blind spots

Every failed workflow creates a GitHub issue at `.github/workflows/nightly-statcast.yml:L161-L173`, with no deduplication or escalation threshold. A source probe that fails repeatedly could create issue noise unless probe failures are recorded separately and escalated only after the SLO is exceeded.

## Correctness concerns to resolve before implementation

1. `player_game_logs` lacks `game_id`. A date plus player key is not a sufficient source identity for exact reconciliation, rescheduled games, or deletion of a withdrawn source row.
2. The schedule mapping stores a date string, not a final timestamp. Game-end scheduling must use kickoff plus an expected duration, a known upstream update window, or an observed source event. It cannot use the current schedule map as an exact final-whistle signal.
3. The source has separate publication behavior for weekly stats, NGS, PFR, schedules, and rosters. A single “source ready” bit may be wrong unless it specifies which assets are required for each output.
4. Current snapshots and Recent Form are derived from different stages. They should share a `refresh_id` or source fingerprint so the app never joins a new snapshot set to an older rollup without that state being explicit.
5. Recent Form's global phase anchor assumes the latest available week is complete enough to anchor every player. Coverage validation must happen before the anchor advances.
6. Live-season qualification uses a scale based on the maximum number of games at `ingest.py:L111-L124`. This is expected behavior, but rankings and qualification flags can change on every refresh. The published source time and as-of week need to be retained for auditability.
7. The source pipeline has had failed scheduled runs. Any architecture that assumes the upstream schedule always runs on time will fail the stated Sunday experience during an outage.
8. A successful GitHub job currently means that the Python commands exited successfully. It does not mean that the latest expected game is present in Supabase.

## Recommended architecture

### 1. Define a freshness contract

Use two timestamps in the product and monitoring contract:

- `source_published_at`: when the required nflverse asset changed.
- `published_at`: when the validated data version became live in Supabase.

Recommended initial service levels:

- Detect a changed source asset within 30 minutes during active NFL windows.
- Publish validated current-season data within 15 minutes after detection, assuming the source and Supabase are healthy.
- Normal game-end-to-user availability target: usually two to three hours, constrained by the upstream source.
- Fallback target: retry through the next source window and daily safety run, with an operator alert if the data is still stale after six hours or by the next morning.

The exact numbers are product decisions. The important distinction is that a game-end SLO and a source-to-Supabase SLO are separate.

### 2. Split probing from ingestion

Add a lightweight probe stage that does not run the Python test suite or write the data tables when nothing changed.

The probe should:

1. Resolve the active season and phase.
2. Check the current release metadata for the required asset, preferably `timestamp.json` plus `ETag` or `Last-Modified`.
3. Record a stable source fingerprint. Include asset name, source timestamp, ETag, and content length. If late corrections can occur without metadata changes, include a content hash or periodic sampled hash.
4. Compare the fingerprint to the last successfully published fingerprint.
5. Exit successfully with `changed=false` when there is no new source version.
6. Start the full refresh only when the fingerprint changed, a readiness gate passes, or a manual `force` input is supplied.

The probe should also inspect source coverage, not just metadata. At minimum, validate that the current season, expected latest week, and expected completed-game count are present before promoting the result.

### 3. Use a hybrid event-aware schedule

A pure game-end trigger is not available from the current repository. The schedule table has kickoff and game-day information, while exact final events arrive with PBP. The practical trigger is a set of post-game source checks with retries.

Recommended first version:

- Keep a daily safety check after the normal source update window.
- Add lightweight checks after the nflverse post-game windows, with backups at approximately +60 and +120 minutes.
- During active NFL months, consider one lightweight check every 30 minutes. It should only fetch metadata and exit without ingestion when the fingerprint is unchanged. This is the most robust way to handle flexed, rescheduled, and unusually long games.
- If the source release API supports event notifications or a reliable webhook later, replace or reduce polling.

The current upstream schedule suggests these UTC windows, subject to validation against the actual current season schedule:

| Source window | Initial probe | Backups |
| --- | --- | --- |
| Thursday night game | Friday 06:15 UTC | 06:45, 07:45 UTC |
| Sunday early games | Sunday 22:45 UTC | 23:15 UTC, Monday 00:15 UTC |
| Sunday late games | Monday 00:45 UTC | 01:15, 02:15 UTC |
| Sunday night game | Monday 06:15 UTC | 06:45, 07:45 UTC |
| Monday night game | Tuesday 06:15 UTC | 06:45, 07:45 UTC |
| Daily safety | Daily 09:45 or 10:00 UTC | Next scheduled probe and operator alert |

These checks must be treated as best-effort dispatches. GitHub scheduled jobs can be delayed. The data fingerprint and daily safety run are what make the system converge after a delayed or missed schedule.

Do not hardcode only weekday assumptions. Derive active windows from the nflverse schedule or a checked-in schedule artifact so flexed and rescheduled games are included.

### 4. Serialize refreshes and identify every run

Add a concurrency group for the data publisher, with `cancel-in-progress: false`, so a source-triggered run queues behind an active run rather than canceling it or interleaving writes.

Create a `data_refresh_runs` or equivalent state table. Suggested fields:

- `refresh_id`
- `source_fingerprint`
- source asset names and source publication timestamps
- start and completion timestamps
- target season and phases
- status such as `probing`, `building`, `validated`, `published`, `failed`, or `degraded`
- row counts by output table
- maximum source week and game date
- expected and observed completed-game counts
- NGS/PFR readiness flags
- error code, retry count, and failure detail

Keep the last successful refresh pointer separate from an in-progress run. A failed or partial run must never become the app's current version.

### 5. Build and publish atomically

The safest design is versioned staging:

1. Read all required source assets for one `refresh_id`.
2. Build snapshots, game logs, and Recent Form into staging tables keyed by `refresh_id`.
3. Validate row counts, uniqueness, required metrics, source coverage, phase, season, and recent-week completeness.
4. Publish one active-version pointer in a database transaction or a single Postgres RPC.
5. Have stable views or RPCs expose only the active version to the app.
6. Retain the prior active version until the new version is proven healthy.

If full versioned tables are too large for the first migration, the minimum acceptable alternative is a transaction that stages each output and swaps or replaces the current season as one unit. Direct delete-then-rebuild against live tables should not remain on the event path.

Snapshots, game logs, and Recent Form should be promoted together, or the status table should explicitly expose a degraded partial state. The preferred product behavior is a consistent set.

### 6. Replace the date watermark with an overlap or game identity

For the first implementation, re-read an overlap of at least the latest seven days or two weeks on every current-season event refresh. This captures late source corrections and late NGS publication without walking the entire season.

The durable design should:

- add `game_id` to `player_game_logs` and include it in the uniqueness key, or maintain an equivalent source-game identity;
- store source version or source row hash per game log;
- reconcile the affected game set, including inserts, updates, and deletions;
- run a periodic full-season reconciliation as a backstop.

Do not use only the current maximum `game_date`. It will not catch a correction to an older game, a late source row, or an asset that temporarily omitted a row.

### 7. Separate core readiness from enrichment readiness

Define which data is required for the user-facing publish:

- Core: weekly player stats, schedule, identities, and all metrics needed for a valid snapshot and game log.
- Enrichment: NGS and PFR fields that may publish on a different cadence.

Recommended behavior is either:

- block the complete version until all required enrichment is ready, or
- publish a clearly marked core version and fill enrichment through a later version without presenting it as fully final.

Do not silently omit NGS or PFR and report a normal healthy refresh. The state table should make the decision visible.

### 8. Improve validation gates

Before any publish, validate at least:

- required source HTTP status and metadata;
- source season and phase match;
- expected completed games and latest completed week;
- expected team coverage for the completed games;
- no duplicate source keys;
- minimum and maximum row counts based on the schedule, not only fixed floors;
- required metrics and non-null rates;
- player identity join coverage;
- Recent Form anchors and window counts;
- no unexpected deletion percentage compared with the active version;
- all output tables use the same `refresh_id`.

Fixed floors such as 150 REG rows and 20 POST rows can remain as emergency guards, but they are not sufficient readiness tests.

### 9. Make failures observable without creating issue spam

Log and retain:

- probe time and source metadata;
- source-to-detection and source-to-publish lag;
- stage durations;
- source row counts and coverage;
- output row counts;
- overlap correction counts;
- active refresh ID and prior refresh ID;
- every failure stage and retry number.

Create a GitHub issue only after a defined threshold, such as repeated failures or a source-to-publish SLO breach. Deduplicate by season, source fingerprint, and failure class. A no-change probe should not create a failure issue.

### 10. Keep the app's cache behavior, but expose honest status

The current fallback to a complete cached current-season dataset is valuable and should remain. Add a small status endpoint or read from the refresh state table so the app can distinguish:

- last successful source time;
- last successful publish time;
- games through and week through;
- current status, such as healthy, source pending, retrying, or degraded;
- whether the displayed data is cached because the newest refresh failed.

The current `DataCoverage` and player `updated_at` fields provide useful pieces but are not enough to distinguish source time from database write time.

Recent Form should carry the same refresh metadata as the snapshot response. Its session cache should be invalidated when the active refresh ID changes, or it should have a short time-to-live during the season.

User-facing copy should move from “nightly refresh” to language such as “Data through Week 1, last checked at 9:08 PM PT” and, when appropriate, “The latest source update is still processing.” This is especially important for a Sunday user who checks shortly after a game.

## GitHub Actions versus MacBook cron

The repository is public. GitHub's current billing documentation says standard GitHub-hosted runner usage is free for public repositories, and self-hosted runners do not incur GitHub-hosted runner charges. Reference: [GitHub Actions billing and usage](https://docs.github.com/en/actions/concepts/billing-and-usage).

Therefore, use GitHub Actions as the primary runner. The main cost optimization is avoiding full source downloads, tests, Supabase writes, and rollups when the source fingerprint did not change.

A MacBook can be an optional emergency dispatcher, but it adds:

- sleep and network availability failures;
- token and secret storage risk;
- duplicate dispatch risk;
- a second place to observe and troubleshoot;
- dependence on a personal machine for production freshness.

If a Mac fallback is later required, it should dispatch the probe or a manual workflow, not hold the Supabase service-role key or independently write production tables. macOS `launchd` is generally a better scheduler than user cron for a laptop, but this should remain a fallback and not the normal path.

## Suggested implementation sequence

### Phase 0, instrument without changing serving behavior

- Record source metadata, run IDs, stage durations, row counts, and max week/game date.
- Confirm the exact semantics and reliability of nflverse timestamps, ETags, and release assets.
- Measure current Supabase publication lag separately from source publication lag.
- Add tests for expected coverage calculations using fixtures.

### Phase 1, add source-aware triggering

- Add a cheap source probe and fingerprint state.
- Add post-game probes, backups, a daily safety run, and manual force.
- Add workflow concurrency.
- Do not promote a source fingerprint until the probe's readiness criteria pass.

### Phase 2, make writes failure-safe

- Add `refresh_id` and run metadata.
- Add staging tables or an atomic database RPC.
- Rebuild Recent Form in staging rather than deleting the active rows first.
- Add overlap reconciliation and, preferably, `game_id`.

### Phase 3, expose freshness to users

- Add backend status response.
- Add active refresh ID and source/publish timestamps to data coverage.
- Invalidate Recent Form cache on active-version change.
- Update “nightly refresh” copy and stale/degraded states.

### Phase 4, tune cadence using observed data

- Compare game-end-to-source, source-to-detection, detection-to-publish, and publish-to-view metrics.
- Reduce or expand probe frequency based on actual misses and source behavior.
- Reassess a Mac fallback only if GitHub schedule delays materially violate the service level.

## Tests and acceptance criteria

The current unit tests cover transformations, but not the operational properties needed here. The repository's 2026-09-09 workflow log showed 93 tests passing before the source asset returned 404, which demonstrates that the test suite can be green while the source is unavailable.

Add tests for:

- unchanged source fingerprint skips full ingestion;
- changed fingerprint starts one refresh;
- manual force bypasses the fingerprint gate;
- overlapping runs serialize;
- late correction before the current max date is re-ingested;
- source row deletion is reconciled;
- missing source asset leaves the active version untouched;
- partial source coverage fails validation and leaves the active version untouched;
- NGS or PFR degradation is recorded and follows the chosen publish policy;
- failed snapshot, game-log, or Recent Form stage cannot expose a partial version;
- Recent Form anchor does not advance on incomplete latest-week data;
- expected game and team coverage is calculated from the schedule;
- workflow schedule and manual inputs are valid;
- app status distinguishes source time, publish time, cached display, and games through;
- active refresh ID invalidates or bypasses stale Recent Form cache.

Acceptance criteria for the event-aware flow should include:

- A normal Sunday game produces a validated backend version during the source's normal post-game window, without waiting for the next day.
- A source update that occurs after the repository probe is picked up by a backup probe or the daily safety run.
- A source 404 or partial asset leaves the prior version available and creates a deduplicated operational signal.
- A retry of the same source fingerprint is safe and converges to the same data.
- A late correction to a prior game is visible after the next refresh.
- The app accurately says which games and week are included, regardless of whether it is showing network or cached data.

## Questions that must be resolved before implementation

1. What is the product SLO: within 30 minutes of source publication, within two hours of final whistle when possible, or another target?
2. Which nflverse asset is the authoritative readiness signal for snapshots and game logs? Is `timestamp.json` updated for late corrections?
3. Must NGS and PFR enrichment block publication, or may core data publish first with a degraded state?
4. Should snapshots, game logs, and Recent Form always become visible together?
5. Can the Supabase project accept the required migration and a transaction-backed RPC or active-version view?
6. Is adding `game_id` to `player_game_logs` acceptable, including migration and reingestion of current data?
7. How much source overlap is acceptable on every event refresh, seven days, two weeks, or game-ID based?
8. What is the expected completed-game definition for a source readiness check, and how should postponed, tied, or corrected games be handled?
9. Should the app show source-pending and degraded states, or only continue showing the last known-good data?
10. What alert threshold is acceptable for repeated source failures, and who owns the upstream dependency escalation?
11. Is a personal Mac fallback worth the operational risk despite public GitHub Actions being free?
12. Can the current nflverse scheduled workflow defect be resolved or confirmed before this cadence depends on its post-game windows?

## Audit conclusion

The requested behavior is feasible and does not require a MacBook for cost reasons. The highest-value change is a source-aware probe plus a readiness-gated, serialized refresh. The highest-risk implementation shortcut would be adding more cron entries to the current live-write job without fixing the date watermark and delete-then-rebuild behavior.

The recommended sequence is to measure and fingerprint the source first, trigger only on new source versions, reprocess an overlap, validate expected game coverage, publish all derived tables atomically, and expose source/publish status to the app. This should make Sunday data appear at the first trustworthy source window while retaining backups and the last known-good dataset during upstream or pipeline failures.

## References

- `.github/workflows/nightly-statcast.yml:L3-L10`, `L35-L71`, `L111-L173`
- `backend/README.md:L3-L7`, `L32-L77`
- `.claude/rules/backend-pipeline.md:L17-L23`
- `backend/ingest.py:L987-L1029`, `L1037-L1150`
- `backend/ingest_game_logs.py:L257-L362`
- `backend/rollup_recent_form.py:L229-L295`, `L298-L435`
- `supabase/schema.sql:L39-L109`
- `StatScout/Services/StatcastAPI.swift:L222-L325`
- `StatScout/ViewModels/DashboardViewModel.swift:L764-L853`
- `StatScout/StatScoutApp.swift:L108-L116`
- `StatScout/Views/SettingsView.swift:L157-L193`
- `backend/tests/test_ingest.py:L553+`
- `backend/tests/test_game_logs.py:L76+`
- `backend/tests/test_rollup_recent_form.py:L1+`
- [nflverse data refresh notes](https://github.com/nflverse/nflverse-data/blob/main/README.Rmd)
- [nflverse scheduled PBP and stats workflow](https://github.com/nflverse/nflverse-pbp/blob/master/.github/workflows/update_data.yaml)
- [GitHub Actions billing and usage](https://docs.github.com/en/actions/concepts/billing-and-usage)
