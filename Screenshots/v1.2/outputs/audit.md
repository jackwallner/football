# Screenshot audit: football-next-statscout

Status: **PASS**
Disposition: **RELEASE-READY**
Target: `iphone_69` at `1320x2868`
Capture status: `ok`

This report combines file-spec checks with an independent thumbnail and OCR pass. Open each `contact-sheet.png` and `search-grid.png` before approving a set.

## Warnings

- fan-first-football-context: 01-know-who-leads-the-league.png: thumbnail OCR missed header words ['know', 'who', 'leads', 'league']
- fan-first-football-context: 02-know-what-makes-a-player-elite.png: thumbnail OCR missed header words ['know', 'what', 'makes', 'player', 'elite']
- fan-first-football-context: 04-scout-your-team-in-one-view.png: thumbnail OCR missed header words ['scout', 'your', 'one', 'view']
- fan-first-football-context: 06-settle-the-debate-side-by-side.png: thumbnail OCR missed header words ['settle', 'the', 'debate', 'side', 'side']
- fan-first-football-context: 07-see-how-a-season-changed.png: thumbnail OCR missed header words ['see', 'how', 'season', 'changed']

## Market brief

- Category: NFL advanced player analytics and comparison apps
- Audience: NFL fans, fantasy and dynasty players, writers, and creators who need fast context on a player's value
- Problem: Box scores tell fans what happened, but not who was efficient, who is changing, or how a favorite fits the league
- Advantage: Football Next: StatScout turns advanced NFL metrics into scannable leaderboards, player profiles, recent movement, team context, following shortcuts, and side-by-side comparisons
- Competitive context: It competes with score apps and dense desktop analytics pages. It wins by making EPA, percentile context, recent form, and fan-selected players readable in one mobile workflow

## Sets

| Set | Status | Frames |
| --- | --- | ---: |
| `fan-first-football-context` | pass | 7 |

## Review contract

- Contract: `single-header-benefit-story-v3`.
- Every creative frame has exactly one large, period-free header capped at two lines. Eyebrows and subheaders are forbidden.
- Phone frames use at least 50% of the canvas for literal UI evidence.
- The selected submission set contains six to eight frames. Other sets and background variants are review alternatives, not additional ASC inventory.
- Every visible header pitches a concrete benefit backed by a per-frame problem, advantage, search term, and literal UI proof.
- The first three frames must communicate separate market value at search scale.
- Every frame declares source, source_evidence, capture_flow, device, and evidence_status. Canonical frames map one-to-one to capture-report records.
- The app screen must be real capture evidence from the referenced build.
- Health and wellness copy must stay complementary and non-diagnostic.
- Re-run the audit after every copy, source, or layout change.
