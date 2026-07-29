# App icon handoff

Separate from the screenshot/marketing handoff one level up. Nothing here touches
`StatScout/Assets.xcassets/` until a concept is picked.

| File | Purpose |
|---|---|
| [`PROMPT.md`](./PROMPT.md) | Paste-ready prompt + the exact four files to attach |
| [`ICON_BRIEF.md`](./ICON_BRIEF.md) | The spec: palette, composition, constraints, deliverables |
| `reference/current-icon-v1-1024.png` | The shipping icon being replaced |
| `reference/ui_leaders.png`, `reference/ui_player_profile.png` | Real UI, for visual identity |
| `output/` | Where Claude Design writes concepts, proof sheets, and `NOTES.md` |

## After a concept wins

```sh
scripts/install_icon.sh claude-design/icon/output/concept_b_cream.png
xcodegen generate
source ~/.football_credentials && bash scripts/testflight.sh
```

`install_icon.sh` validates 1024×1024, flattens any alpha channel (App Store rejects icons with
alpha), and writes to the asset catalog's single-size `AppIcon.png`.
