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

# Mobile user-experience reviewer supplement

Reviewer: skeptical iOS product and UX review

Focus: a mobile user checking on Sunday immediately after a game, including refresh timing, foreground behavior, loading, errors, stale data, caching, manual refresh, partial stats, and accessible communication.

## Mobile verdict

The current app has the right instinct in one important area: it protects a complete cached dataset when a remote response is incomplete. The user experience does not yet make that protection legible. A user can see old data without knowing it is old, see no recent games while the source is still pending, or see refreshed snapshot data beside cached recent form.

The mobile requirement should be framed as “show the newest trustworthy revision as soon as it is published, and explain the wait before then.” A final advanced metric cannot be promised at the final whistle because the source itself usually needs time. The 2025 Sunday regular-season source timing was a median of 1.91 hours and a P90 of 2.70 hours. The app should use that reality to provide a clear pending state rather than pretending the data is either instantly final or completely unavailable.

The minimum acceptable experience is:

1. Load the last known-good data immediately.
2. Check the shared refresh state when the app opens or returns to foreground.
3. Show a visible, plain-language status next to the data.
4. Invalidate snapshots, recent form, team logs, and player logs as one refresh revision.
5. Keep old content visible while checking or retrying.
6. Never turn source-pending data into a misleading “no games” result.
7. Give every stats surface a refresh action.

## What a Sunday user expects

The user does not think in terms of parquet releases, Supabase rows, or separate snapshot and rollup jobs. Their mental model is:

- The game ended.
- The app should know soon.
- If it does not know yet, the app should say why.
- When the numbers arrive, the screen should update without requiring a scavenger hunt.
- If the data is delayed or unavailable, previously useful numbers should remain available and visibly dated.

The product should distinguish three moments:

| Moment | What the user wants | What the system can honestly provide |
| --- | --- | --- |
| Final whistle | A confirmation that the app is tracking the game | Pending source state and last complete data |
| Source publication | Updated player and game data | Pipeline should process and validate promptly |
| App foreground or refresh | New results on the current screen | Shared refresh and cache invalidation |

The current implementation has no user-visible object that connects those moments.

## Current mobile evidence

| Finding | Evidence | UX consequence |
| --- | --- | --- |
| Foreground refresh only calls the main load | StatScout/StatScoutApp.swift:108-115 | Returning to the app can refresh snapshots without refreshing dependent recent form |
| Recent-form cache returns when a window is already loaded | StatScout/ViewModels/DashboardViewModel.swift:426-486, especially :444 | Trends may keep showing a pre-game result |
| Profile recent logs use local cache state | StatScout/Views/PlayerProfileView.swift:959-984 | A player profile can retain old recent games after a background return |
| Team form uses local task and load state | StatScout/Views/TeamFormCard.swift:100-116, :506-523 | Team roster and team form can update at different times |
| Dashboard has pull-to-refresh | StatScout/Views/DashboardView.swift:22-42 | One surface has recovery behavior, other data surfaces do not |
| Trends has no refresh control | StatScout/Views/HotColdView.swift:77-150, :274-311 | A Pro user cannot refresh where the stale data is visible |
| Team view has no shared refresh control | StatScout/Views/TeamView.swift:207-270 | Team users may need to leave the screen to try again |
| Profile has loading, error, and no-games states | StatScout/Views/PlayerProfileView.swift:642-673 | Pending source data can be mistaken for no player activity |
| RecentFormCard has a player-only task identity | StatScout/Views/RecentFormCard.swift:49-65 | Season or phase changes should be regression-tested for stale task reuse |
| Coverage is mainly exposed in Settings/About | StatScout/Views/SettingsView.swift:157-200 | Freshness is hidden from the decision surface |
| Existing data suppresses the main loading indicator during refresh | StatScout/ViewModels/DashboardViewModel.swift:770-844 | Foreground work is silent |
| Current cache can live for 48 hours | StatScout/Services/PlayerCache.swift:8-16, :63-103 | Useful offline behavior can look current if age is not shown |
| API fetches bypass URL cache but have no explicit retry policy | StatScout/Services/StatcastAPI.swift:97-326 | A transient failure can become a visible error too quickly |
| All tabs remain mounted | StatScout/Views/RootTabView.swift:114-126 | Navigation state is preserved, but so are view-local stale caches |
| There is no background task or push refresh path | StatScout/StatScoutApp.swift and repository-wide search | A closed app cannot be expected to update until the next launch or foreground |

## Sunday journey audit

### 1. User opens the app while the game is still finishing

Expected:

The app should not claim that final advanced stats are available. It should show the latest complete dataset and, if the schedule says a game is in progress or recently ended, a calm “updating” state.

Current risk:

The main dashboard can show existing data, but there is no shared game-aware status. The user has no way to tell whether the displayed week includes the game.

Recommendation:

Show the last complete coverage point and a non-blocking “Game data is still arriving” status. Do not block the app or replace the content with a blank spinner.

### 2. User opens the app minutes after the final whistle

Expected:

The app should check for a new refresh revision. If the source is not ready, it should explain that final advanced stats are still pending and give the last complete game or week.

Current risk:

The current once-daily workflow may not have updated Supabase yet. The app can only fetch what is there and does not know whether the upstream source is pending or the pipeline is broken.

Recommendation:

Read the central refresh status. Show pending versus failed separately. Set a next-check time or a simple “We will keep checking” message.

### 3. User manually pulls to refresh

Expected:

The refresh should check the source-backed status, keep the current content visible, and return a meaningful result.

Current risk:

Pull-to-refresh is only on Dashboard. On Dashboard, existing data makes isLoading false, so the refresh may be visually subtle. On other screens there may be no action.

Recommendation:

Use one shared refresh coordinator. Expose it from a banner button everywhere. Keep native pull-to-refresh where available, but do not make it the only way to recover.

### 4. User remains on Trends while data becomes ready

Expected:

The screen should update the 3, 5, and 8-game windows or clearly show that its current data is still through the previous week.

Current risk:

HotColdView.swift:123-150 calls loadRecentFormIfNeeded. DashboardViewModel.swift:444 returns existing recent-form cache. A foreground snapshot refresh does not guarantee that the Trends cache changes.

Recommendation:

Attach recent-form cache validity to the active refresh revision. When the revision changes, invalidate the affected windows and reload the active Trends view. Show “Updated through Week N” after completion.

### 5. User visits a player profile after refresh

Expected:

Snapshot metrics and recent-game form should describe the same season, phase, and source revision.

Current risk:

PlayerProfileView.swift:959-984 uses local state and a local cache key. RecentFormCard.swift:49-65 uses a narrow task identity. A failed request can also end in an ambiguous no-games presentation.

Recommendation:

Use a shared source revision and explicit pending state. Render “No qualifying games” only after the source is known ready for that game/week.

### 6. User checks later that night after a source delay

Expected:

The app should show new data if ready, or clearly say it is still waiting. Repeated manual attempts should not create confusing duplicate work.

Current risk:

The pipeline can be delayed until the next daily schedule. GitHub schedule timing is best effort. The app has no next-check or last-check display near the relevant data.

Recommendation:

Display “Last checked” and “Stats through” separately. If the newest source revision is ready, reload. If not, retain the last complete state.

## Loading, error, stale, and partial-state review

A single Boolean such as isLoading cannot represent the states the user needs. The app needs a typed status model or equivalent shared state.

| State | Meaning | Content behavior | User action |
| --- | --- | --- | --- |
| Ready | The newest validated revision is live | Show normal content and coverage | Optional refresh |
| Checking | A check or refresh is in progress | Keep current content visible, show activity | Wait or cancel if supported |
| Pending | A completed game is expected, but the source is not ready | Show last complete data with pending message | Check again |
| Partial | Some relevant source data arrived, but the completeness gate is not met | Keep last complete data as primary; optionally show progress | Check again later |
| Stale | The newest expected revision is late beyond the normal window | Show cached content with age and warning | Retry |
| Offline | The device cannot reach the service | Show cached or bundled content with offline label | Retry when connected |
| Failed with data | The latest attempt failed, but a prior valid revision exists | Keep prior content and explain failure | Retry |
| Failed without data | No cached or bundled valid data exists | Show recovery state and diagnosis | Retry or continue offline |

The app should never use “No games in the last N games” as a substitute for Pending or Partial. That text is appropriate only after source readiness is established and the player genuinely has no qualifying rows.

## Recommended user-facing flow

### Initial launch

1. Render the newest valid disk or bundled dataset immediately.
2. Show a compact status line with “Stats through [game/date/week].”
3. Fetch the refresh state and compare its active revision to the displayed revision.
4. If a newer ready revision exists, refresh the core data and active screen.
5. If the state is pending or partial, leave the current content in place and show the reason.
6. If the state is failed or stale, show the age and a retry action.

The initial cached render should not wait for the network.

### Foreground return

1. Check whether the last foreground check is within the throttle window.
2. If it is not, fetch the shared refresh state.
3. If the active revision is unchanged and status is ready, do not reload every screen.
4. If the revision changed, invalidate snapshot, recent-form, team-log, and player-log caches.
5. Reload the active screen and update its coverage label.
6. If the source is pending, show a non-blocking pending message rather than a generic error.
7. If the request failed, leave content in place and show “Last checked” plus retry.

A five-minute routine throttle is a reasonable starting point. During an active pending window, the status can be checked more frequently because the check should be lightweight.

### Manual refresh

The same action should serve Dashboard pull-to-refresh, an explicit banner button, and any retry button:

- Deduplicate concurrent requests.
- Show a checking indicator without clearing content.
- Return a typed result: unchanged, updated, pending, partial, stale, offline, or failed.
- Update the shared status regardless of which screen initiated the action.
- Invalidate dependent caches only after a new validated revision is available.

### When new data becomes ready

The user should receive a subtle, non-blocking confirmation:

“New game data is available. Player form updated through Week 2.”

On Trends, Teams, and Profile, the active screen should update without forcing the user back to Dashboard. The visible revision and coverage label should change together.

## Recommended freshness component

A shared status row should appear near the top of Dashboard, Trends, Teams, profiles, and stats boards. It should be compact in the normal ready state and expand for pending or failed states.

Normal state:

“Stats through Week 2, Sunday 4:25 PM. Updated 8 minutes ago.”

Pending state:

“The game ended, but advanced stats are still arriving. Showing complete data through Week 1. Check again.”

Partial state:

“Some Week 2 data is available. We are waiting for the complete game set before updating recent form.”

Stale state:

“Showing saved data through Week 1. The latest source update is later than usual.”

Offline state:

“You are offline. Showing saved data through Sunday.”

The component should include:

- an accessible status label;
- coverage date or week;
- optional last-checked timestamp;
- refresh or retry action;
- no color-only meaning;
- no Pro gate for the basic status.

Settings can retain the detailed source and pipeline timestamps, but it should not be the only place where freshness is explained. “Nightly Refresh” should become “Data updates” if the cadence changes.

## Persona-specific mobile concerns

### Sunday casual fan

Needs a fast, calm answer and may not know that advanced stats have a source delay. The key failure is ambiguity. The app should say “still arriving” instead of showing an apparently current but old number.

### Sunday fantasy analyst

Needs a trustworthy boundary. The key failure is partial data appearing complete. Show game count and last complete coverage when a week is not ready.

### Pro Trends user

Paid specifically for recent form. The key failure is the Dashboard updating while Trends remains cached. A successful refresh must invalidate all active recent-form windows.

### Pro player-profile user

Needs season, phase, and window consistency. The key failure is stale or mismatched local task state. The card should expose its coverage and source revision indirectly through the shared status.

### Team follower

Wants roster and team form to update together. The key failure is separate local team tasks. Use one team-level refresh action and a single coverage label.

### Free user

Needs to understand freshness without an upsell. Pending and stale status should be available in the free experience, with advanced source detail optional.

### Offline user

Needs useful data more than a blank error. Preserve cached content, show its age and coverage, and make retry safe.

### VoiceOver user

Needs status conveyed as text, not color or animation. Announce material transitions such as pending to ready, and ensure the refresh button identifies its action and current state.

### Large Dynamic Type user

Needs the pending explanation and coverage date to remain readable. Avoid truncating the status into a generic ellipsis. The action should remain reachable without precision scrolling.

### Returning user who leaves the app open

May stay on one tab through multiple source updates. The key failure is stale retained view state. A shared refresh revision must reach retained tabs, not only newly created views.

## Mobile acceptance criteria

### Visibility and trust

- Every data screen identifies the last complete game, date, or week.
- Every screen can distinguish ready, checking, pending, partial, stale, offline, and failed.
- Pending source data is never described as no games or final stats.
- A failed refresh leaves the last known-good content visible when it exists.
- The age of cached or stale content is visible in plain language.
- “Last checked” is not presented as equivalent to “stats through.”

### Foreground and cache behavior

- Returning to foreground checks the shared refresh state subject to a documented throttle.
- A new active revision invalidates snapshot, recent-form, team-log, and player-log caches.
- Trends, Team, and Profile update while the user remains on those screens.
- The app does not issue overlapping equivalent refreshes from foreground and manual refresh.
- A source revision with the same game count but a newer timestamp can still invalidate caches and update corrections.
- Changing player, season, phase, or window cannot reuse an incompatible task result.

### Refresh affordances

- Dashboard retains pull-to-refresh.
- Trends, Teams, profiles, Standard Stats, Best/Worst, and Compare have an equivalent refresh or retry action where the content can be stale.
- A refresh action reports unchanged, updated, pending, partial, stale, offline, or failed rather than only stopping a spinner.
- Existing content remains visible during refresh.

### Accessibility

- VoiceOver reads the status category, coverage, and action.
- Status does not rely on color alone.
- Dynamic Type does not truncate the essential status or retry action.
- A status transition from pending to ready is announced once, without repeated announcements on every render.
- The loading indicator has a meaningful label.

### Validation and testability

- View-model tests cover each status state and all transitions.
- Tests cover foreground refresh while Trends, Team, and Profile state are already mounted.
- Tests cover source pending versus a genuinely empty player result.
- UI tests cover the banner, refresh action, stale content, and accessibility labels.
- Test fixtures can simulate the first 2026 game being source-ready while the app pipeline is not yet run.
- Test fixtures can simulate the 67-row partial source observed in current 2026 runs.
- The existing CI gap for UI tests is tracked and does not silently remove coverage for this journey.

## Product and implementation boundary

This supplement makes recommendations only. It does not authorize or include changes to Swift, Python, GitHub Actions, Supabase, or tests.

The immediate product decision should be the freshness contract:

- The app promises the newest trustworthy source-backed data, not instant final metrics.
- The normal Sunday expectation is roughly three hours or less when the source behaves normally.
- The app explains pending data before that point and stale data after the normal window.
- The backend owns completeness; the app owns clear communication and cache invalidation.
- A MacBook is not required for cost control. GitHub Actions remains the recommended primary worker, with a Mac dispatcher considered only after measured scheduler lag.

No code changes are included in this reviewer supplement.

## Audit artifact verification

- Markdown whitespace check passed with git diff --check.
- A repository-wide check found no em dash added by this task.
- The backend test command was attempted from backend with python3 -m pytest tests. Collection was blocked by the environment because nflreadpy is not installed. No application code was changed to work around that dependency issue.
- Existing unrelated working-tree changes remain outside this document. The task-owned diff is laudit912.md only.

## Supplemental source and persona audit notes

This section adds the source-level measurements and user-experience review completed on 2026-09-12. It is intentionally appended to preserve the earlier audit notes in this tracked document.

### Source release snapshot observed on 2026-09-12

The nflverse data README is the primary public timing reference. It describes raw JSON as generally appearing about 1-2 hours after a game, PBP and player stats around 09:00 UTC during the season, rosters around 07:00 UTC, NGS around 07:00 UTC, and PFR advanced stats several times per day.

The upstream workflow files were also inspected:

- [nflverse-pbp update workflow](https://github.com/nflverse/nflverse-pbp/blob/main/.github/workflows/update_data.yaml)
- [nflverse-pbp workflow runs](https://github.com/nflverse/nflverse-pbp/actions/workflows/update_data.yaml)
- [NGS update workflow](https://github.com/nflverse/ngs-data/blob/main/.github/workflows/update_ngs.yaml)
- [PFR advanced stats workflow](https://github.com/nflverse/pfr_scrapR/blob/main/.github/workflows/update_advanced_stats.yaml)
- [roster update workflow](https://github.com/nflverse/nflverse-rosters/blob/main/.github/workflows/update_rosters.yaml)
- [nflreadpy player-stat loader](https://github.com/nflverse/nflreadpy/blob/main/src/nflreadpy/load_stats.py)
- [nflreadpy NGS loader](https://github.com/nflverse/nflreadpy/blob/main/src/nflreadpy/load_nextgen_stats.py)
- [nflreadpy PFR loader](https://github.com/nflverse/nflreadpy/blob/main/src/nflreadpy/load_pfr_advstats.py)
- [nflreadpy schedule loader](https://github.com/nflverse/nflreadpy/blob/main/src/nflreadpy/load_schedules.py)
- [nflreadpy downloader](https://github.com/nflverse/nflreadpy/blob/main/src/nflreadpy/downloader.py)

The following timestamp manifests were read during this audit. These are point-in-time observations, not permanent guarantees.

| Release tag | Observed last_updated UTC | Manifest |
| --- | ---: | --- |
| pbp | 2026-09-12 12:50:48 | [pbp timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/pbp/timestamp.json) |
| stats_player | 2026-09-12 12:52:24 | [stats_player timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/stats_player/timestamp.json) |
| nextgen_stats | 2026-09-12 11:23:41 | [nextgen_stats timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/nextgen_stats/timestamp.json) |
| pfr_advstats | 2026-09-12 20:16:48 | [pfr_advstats timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/pfr_advstats/timestamp.json) |
| schedules | 2026-09-12 20:46:30 | [schedules timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/schedules/timestamp.json) |
| rosters | 2026-09-12 11:37:38 | [rosters timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/rosters/timestamp.json) |
| players | 2026-09-12 11:52:47 | [players timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/players/timestamp.json) |
| snap_counts | 2026-09-11 16:10:07 | [snap_counts timestamp.json](https://github.com/nflverse/nflverse-data/releases/download/snap_counts/timestamp.json) |

PBP and stats_player were published within about two minutes in this source cycle, but schedules and PFR were later. The source components are not one atomic release. A monitor must track the required source set for each output rather than use whichever one has the newest timestamp.

### Source family and consumer matrix

| Source | Source cadence or observed behavior | Local consumer | Important lag or completeness note |
| --- | --- | --- | --- |
| Raw PBP | Approximately 1-2 hours after a game in the source documentation, with additional game-window workflow schedules | Indirect input to stats_player; useful for game coverage validation | Early data can be recalculated. The first release is not a finality guarantee. |
| stats_player | Published by the PBP/stats workflow after PBP processing | Snapshot and game-log ingestion | Base dependency. A release can be 404, empty, or incomplete during a transition. |
| NGS weekly | Daily source workflow around 07:00 UTC in January, February, and September through December | Weekly metrics in player_game_logs | Can lag stats_player. Current code catches failures and continues without NGS. |
| NGS season | Same release family, season-level rows | Season-level player_snapshots | Season-level availability can differ from weekly availability. |
| PFR weekly | Workflow polls approximately every six hours during its active months | Not currently consumed by the app pipeline | Current-week scrape failures can leave individual games absent. |
| PFR season defense | Season-level release used by backend/ingest.py | Advanced defense metrics in player_snapshots | Current asset inspected had seasons 2018-2025 and no 2026 row. It cannot provide live 2026 PFR defense to the current consumer. |
| schedules | Separate release with its own timestamp | Required by player_game_logs to assign game dates and IDs | Stats and schedules can be at different generations. |
| rosters and players | Daily metadata updates | Names, identity, and display fields | Soft dependency for numeric stats. A player can reach stats before metadata catches up. |
| snap counts | Approximately four polls per day | Not a core current app dependency | Do not block base player data on this feed. |
| derived snapshots | Local aggregation and percentile computation | player_snapshots | A successful write does not prove optional NGS or PFR completeness. |
| derived game logs | Weekly stats plus schedules, with optional NGS | player_game_logs | Current incremental mode reprocesses only the latest stored game date and can miss older corrections. |
| Recent Form | Rebuilt from game logs and anchored on latest available week | player_recent_form | Must be generated from the same source generation as snapshots and logs. |
| app response | Fetch on launch, foreground, or manual refresh | iOS screens | A new database version is not visible in an already-open app until it fetches. |

### 2025 source workflow reliability

For the 2025 season operational window, the source PBP workflow had 293 recorded runs from 2025-09-01 through 2026-02-28:

- 278 successful scheduled runs;
- eight successful manual runs;
- five failed scheduled runs;
- one cancelled manual run;
- one failed manual run.

The NGS workflow had 187 runs in that same window:

- 174 successful scheduled runs;
- four successful manual runs;
- seven failed scheduled runs;
- two failed manual runs.

The PFR advanced-stat workflow had 603 runs from 2025-09-01 through 2026-02-15:

- 594 successful scheduled runs;
- one successful manual run;
- seven failed scheduled runs;
- one failed manual run.

These are workflow-level measurements. A successful run is not proof that every file or game in the release is complete. They do show why the local controller needs retries and a last-known-good policy even when upstream schedules are regular.

### Exact 2026 asset observations

The first two 2026 games were measured against the source job logs while detailed logs were still available.

| Game | Final PBP event | PBP upload | stats_player upload | Final-event to stats_player |
| --- | ---: | ---: | ---: | ---: |
| New England at Seattle | 2026-09-10 03:24:41 UTC, 2026-09-09 20:24:41 PDT | about 04:40 UTC | 04:41:54 UTC | about 1 h 17 m |
| San Francisco at Los Angeles | 2026-09-11 03:23:53 UTC, 2026-09-10 20:23:53 PDT | about 06:07 UTC | 06:08:41 UTC | about 2 h 45 m |

The source asset sizes also show that the second game was available to PBP/stats_player before the local scheduled refresh:

- The current 2026 PBP file had 323 rows across two games.
- The current 2026 player-stat weekly file had 135 rows across 134 players.
- NGS had week-one weekly rows for passing, rushing, and receiving.
- The local 2026-09-11 scheduled run built 112 game-log rows and 324 Recent Form rows after seeing both games.
- The local 2026-09-12 run completed around 12:27 UTC, before the later PBP and stats_player source timestamps at approximately 12:50 and 12:52 UTC.

The current PFR weekly files were not equivalent to the base release. They contained 2026 week-one rows for New England at Seattle, but not San Francisco at Los Angeles at the time of inspection. Current-week PFR workflow attempts showed scrape and parsing failures. This is a metric-specific source gap, not evidence that the base game was absent from nflverse.

### Metric-specific freshness policy

Treat these metrics as separate products:

1. **Box-score and core player stats.** Use stats_player plus schedules as the base readiness set. Target publication within three hours of the final play on a normal Sunday, with a six-hour alert threshold.
2. **Raw PBP-derived metrics.** Expect the same general window as stats_player, but permit later recalculation. Recheck the current week after the slate completes.
3. **NGS weekly metrics.** Expect them after the daily NGS run. Do not turn a missing NGS value into zero. Either publish core data with an explicit advanced-pending state or wait for NGS only for screens that require it.
4. **NGS season metrics.** Validate season-level row coverage independently. A weekly NGS row does not prove a complete season-level snapshot.
5. **PFR weekly metrics.** Keep them independent of base readiness. A PFR scrape can fail while player stats are usable.
6. **PFR season defense.** Do not claim live 2026 PFR advanced defense until the consumer path has current-season rows or a deliberate weekly aggregation path.
7. **Schedules.** Require a schedule row for each completed game before writing game logs. Treat the schedule source as a separate generation.
8. **Recent Form.** Rebuild after the matching game-log generation is validated. A newly written Recent Form row with an old as_of week is not fresh coverage.
9. **Corrections.** Reprocess the latest two game dates or the full current week on each event refresh. Recheck the prior week daily for 48 hours, then nightly until the next slate.

The current backend already comments that latest-date rows are reread for late updates, but game-date-only overlap is not enough. A correction to an earlier game is skipped once a later game date exists.

### Source release detection

The recommended pre-ingest check is:

1. Read timestamp.json for every source in the output's required-source set.
2. Compare each source timestamp with the last successfully published manifest.
3. If no required source is newer, exit without rewriting the database.
4. If a source is newer, download the candidate assets with a fresh cache policy.
5. Verify asset existence, schema, non-zero size, row counts, current season, and expected game coverage.
6. Read timestamp.json again after a short delay, or otherwise require the multi-asset release to be stable.
7. Build into a candidate refresh ID.
8. Publish only after all required checks pass.

Persist at least:

- source name and release tag;
- source_updated_at;
- checked_at;
- exact asset path;
- ETag or Last-Modified when available;
- byte length or checksum;
- source row count;
- maximum week and game date;
- expected and observed completed-game count;
- optional-source status;
- pipeline start and completion;
- refresh ID and active-version pointer.

A timestamp advance is necessary but not sufficient. A source can publish a newer but partial file, and a correction can arrive with the same maximum week. Coverage and regression checks must accompany timestamp checks.

nflreadpy's loader and downloader use fixed release-tag URLs and cache controls, but the loader does not itself provide the product-level comparison against timestamp.json required here. The wrapper should own that comparison. In CI, a fresh process reduces local cache risk, but a long-lived filesystem cache must not be allowed to hide a newer source release.

### Recommended operating flow

Use a source-aware hybrid controller:

- During active NFL windows, run a cheap monitor every 30 minutes or around known upstream post-game windows.
- Keep the current daily source-window run as a backup.
- Trigger the full ingest only when a source generation changes or a manual force is requested.
- Serialize full ingests with one concurrency group.
- Reprocess at least the latest two game dates, preferably the current week.
- Stage snapshots, game logs, and Recent Form under one refresh ID.
- Validate coverage and source completeness.
- Promote one active version atomically.
- Retain the prior complete version if any stage fails.
- Alert on source-new-but-local-stale, expected-game-missing, source regression, repeated source failure, and publish-SLO breach.
- Reduce polling in the offseason, when source updates stop or change cadence.

Suggested target states:

| State | Meaning | Serving behavior |
| --- | --- | --- |
| unchanged | No required source generation changed | Keep the current version and do not touch updated_at |
| waiting_for_source | A game is over but source has not advanced | Keep last complete data and show source pending if useful |
| ready | Base source set is newer and validated | Build and publish core data |
| partial | Core is valid, NGS or PFR is late | Publish core with explicit advanced-pending status, or hold only the affected product |
| regressed | Candidate loses expected games, rows, or required fields | Hold the previous version and alert |
| complete | Product-required sources are valid | Mark the refresh complete |
| failed | Download, transform, validation, or write failed | Hold the previous version and record the failure |

### App-facing freshness contract

The app should not infer freshness from one updated_at value. Return or expose:

- source publication time for each important source;
- last validated Supabase publish time;
- games through date and week;
- active refresh ID;
- completeness or partial status;
- cached-versus-network state when relevant;
- last successful refresh and last check.

Use “Games through Week N” as the primary user-facing coverage statement. Use “Last checked” and “Last published” as secondary details. Reserve “Last updated” for a clearly defined local write event.

The existing foreground reload and API cache-bypass behavior are sufficient for the app to see a new version once the user opens or refreshes it. They do not provide real-time visibility while the app is already open. A backend push signal is not required for the first iteration, but it should not be implied by the refresh copy.

### Persona review

These are independent review lenses applied directly in this audit because a callable subagent runtime was unavailable.

| Persona | Primary need | Risk in current flow | Recommended response |
| --- | --- | --- | --- |
| Sunday fantasy or analytics user | See finished-game changes Sunday night | Once-daily local refresh waits for the next scheduled run | Post-game source monitor, games-through copy, advanced-pending state |
| Casual fan | Know whether the app is current without source knowledge | Generic Updated time can mean only that old data was rewritten | Show coverage week/date separately from local write time |
| Power user or analyst | Reproduce and trust metrics | PBP, stats_player, NGS, PFR, and schedules can be different generations | Expose source generations and correction state in diagnostics |
| StatScout+ subscriber | Receive timely, coherent Recent Form | Recent Form can be fresh in write time but stale in source coverage | Tie it to the same refresh ID as snapshots and game logs |
| Offline or poor-network user | Keep a useful last-known view | A failed candidate could replace good cached data | Preserve the last complete network and disk versions |
| Accessibility reviewer | Understand partial and stale states without color | A colored status badge can hide an important distinction | Use text labels and accessible coverage dates |
| Support operator | Explain a missing Sunday game | Current logs do not form a normalized source-to-publish timeline | Store run ID, source timestamp, expected games, observed games, and failure class |
| Security owner | Keep one safe production writer | A personal Mac and the local backend .env create credential and target risks | GitHub remains the writer; add a Football-project environment guard |
| Cost-conscious owner | Improve freshness without buying infrastructure | Mac cron adds maintenance but does not reduce source lag | Use free public-repository Actions and no-op source probes |
| Product owner | Balance “fast” against “trustworthy” | Publishing a partial week can make rankings and Recent Form misleading | Define core versus enrichment readiness before changing cadence |

### MacBook cron decision

A MacBook Pro cron is not required for cost control. GitHub's [Actions billing documentation](https://docs.github.com/en/actions/concepts/billing-and-usage) states that standard GitHub-hosted runners in public repositories are free, and self-hosted runners do not incur GitHub-hosted runner charges.

Use GitHub Actions as the primary scheduler because it is auditable, shared, and available without requiring the laptop to be awake. Keep a Mac as a break-glass manual dispatcher only if needed. Do not create a second independent production writer. If a laptop fallback is ever formalized, use macOS launchd and dispatch a safe workflow rather than storing a service-role key locally.

### Updated recommendation

The source timing supports a practical Sunday experience:

- expect base data to become source-available about two hours after a normal game;
- detect it within 30 minutes;
- publish within about 15 minutes after successful detection;
- alert at six hours for a normal Sunday game;
- use wider 8-12 hour windows for Saturday and postseason games;
- keep checking for corrections for 24-48 hours;
- make NGS and PFR lag visible without blocking core stats;
- show the user the coverage week/date and last complete publish time.

The dominant avoidable delay is the local once-daily schedule. More cron entries without source fingerprints, coverage checks, overlap reprocessing, serialization, and atomic publication would improve timing but leave the main correctness risks intact.
