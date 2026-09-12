# Football 1.2 screenshot set

The manifest in this directory is the source of truth for the App Store set.
The seven frames are captured from real shipped views:

1. league leaders
2. player profile
3. Trends
4. team context
5. Following
6. player comparison
7. year history

The capture provider is ScreenshotFixtureAPI. It is compiled only in DEBUG
and is selected only with "-ScreenshotData". The rows are fictional and
deterministic, but the screens, navigation destinations, controls, and
accessibility paths are the product UI.

## Capture

The normal command leases the shared iPhone 17 Pro pool device through shotflow:

~~~sh
shotflow capture screenshots/v1.2/manifest.json --flow repo-command
~~~

The raw phone proof is 1206 by 2622. The renderer produces the App Store
deliverables at 1320 by 2868 and audits them as opaque RGB PNGs:

~~~sh
shotflow all screenshots/v1.2/manifest.json
shotflow audit screenshots/v1.2/manifest.json --release
~~~

The app hook in StatScoutApp.init must select ScreenshotFixtureAPI before
the Supabase configuration branch and call ScreenshotFixtureAPI.prepareUserDefaults().
That prepares the favorite team, followed players, onboarding state, and
standard Stats board entirely in the local simulator.

## iPad proof

The app is universal. The same UI test class can be run on a leased or
throwaway 13-inch iPad in portrait:

~~~sh
scripts/capture-v1.2-screenshots.sh <ipad-udid> screenshots/v1.2/raw-ipad
~~~

That command writes raw iPad proof to raw-ipad. Do not use those files in the
iPhone submission set. Validate iPad output with:

~~~sh
python3 - <<'PY'
from pathlib import Path
from PIL import Image

paths = sorted(Path("screenshots/v1.2/raw-ipad").glob("0*_*.png"))
assert len(paths) == 7, paths
for path in paths:
    with Image.open(path) as image:
        assert image.size == (2064, 2752), (path, image.size)
        assert image.mode in {"RGB", "RGBA"}, (path, image.mode)
print("iPad raw proof is 2064x2752")
PY
~~~

No iPad proof is uploaded by this workflow.
