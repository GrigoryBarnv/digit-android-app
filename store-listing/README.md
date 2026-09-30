# Play Store listing assets

Graphics required by the Google Play Console store-listing process. This
folder holds the source files we upload under Play Console → Grow users →
Store presence → Main store listing.

## Status

- **App icon**: ready to upload (`icon/opentouch-icon-512.png`). Built by
  padding the 432x432 adaptive-icon foreground layer onto a 512x512 white
  canvas (the artwork only fills ~55-60% of its original canvas as
  safe-zone padding, so no upscaling was needed) and adding an alpha
  channel. Meets the 512x512, 32-bit PNG with alpha requirement.
- **Feature graphic**: ready to upload
  (`feature-graphic/opentouch-feature-graphic-1024x500.png`). 1024x500,
  24-bit PNG, no alpha.
- **Screenshots**: ready to upload (`screenshots/phone/`, 9 images).
  Real on-device photos (not simulator renders) padded to exactly 9:16
  with a color-matched letterbox (no visible seam) rather than cropped,
  so no UI is cut off. Cover: idle dashboard, DIGIT live preview + device
  info, RGB controls, FPS/resolution panel, GelSight Mini pairing +
  device info, live ML inference overlay, and the about/credits screen.
  Play Console only accepts up to 8 per device type, so drop one before
  uploading - `04_rgb_controls.png` is the most redundant with
  `05_fps_resolution.png` if you need to cut exactly one.
  `screenshots/tablet-7in/` and `tablet-10in/` are still empty.

## Screenshot requirements (Play Console)

- 2-8 screenshots per device type.
- JPEG or 24-bit PNG (no alpha).
- Each side between 320px and 3840px.
- Aspect ratio between 16:9 and 9:16 (i.e., not too wide or too tall).

Folders:
- `screenshots/phone/` - phone screenshots (required, at least 2).
- `screenshots/tablet-7in/` - 7-inch tablet screenshots (optional, but
  DIGIT 360/tablet rotation support makes these worth including).
- `screenshots/tablet-10in/` - 10-inch tablet screenshots (optional).

## Icon requirements (Play Console)

- 512x512, 32-bit PNG (with alpha channel).
- No transparency in the actual icon artwork itself is fine, but the file
  format must support an alpha channel.

## Feature graphic requirements (Play Console)

- 1024x500, JPG or 24-bit PNG.
- Displayed at the top of the store listing page.

## CI/CD: publishing to Google Play from GitHub Actions

`.github/workflows/play-store-release.yml` builds a signed release App
Bundle (`:app:bundleRelease`) and uploads it to a Play Console track using
[r0adkll/upload-google-play](https://github.com/r0adkll/upload-google-play).
It runs manually from the Actions tab (pick the target track: internal /
alpha / beta / production) or automatically on a `v*` tag push, which
always targets the `internal` track - promoting further is a deliberate
step you take afterward, never automatic.

This needs one-time setup that has to be done by whoever administers the
Play Console listing and the Google Cloud project (requires their own
Google account access - not something that can be scripted from here).

### 1. Generate a real release keystore

The `internalRelease` signing config in `app/build.gradle.kts` (a keystore
checked into this repo, with its password in plain text) is **only** for
internal testing builds installing cleanly across the team. It must never
sign anything uploaded to Play Console, since its password has been public
in git history since the day it was committed. Generate a fresh keystore
that never gets committed anywhere:

```
keytool -genkeypair -v -keystore play-store-release.keystore \
  -alias opentouch-play -keyalg RSA -keysize 2048 -validity 10000
```

Keep the resulting `play-store-release.keystore` file and both passwords
somewhere safe (a password manager, not a repo) - if this key is ever
lost, Google cannot rotate it into an already-published app for you.

### 2. Create the Play Console listing's first release manually

Google's Play Developer API cannot create an app's very first release -
the very first `.aab` for `com.opentouch.android` has to be uploaded by
hand once, through Play Console → your app → Internal testing → Create
release, signed with the same keystore from step 1. Every release after
that first one can go through the API/this workflow.

### 3. Create a Google Cloud service account with Play access

1. In [Google Cloud Console](https://console.cloud.google.com/), either
   use an existing project or create one, then enable the **Google Play
   Android Developer API** (APIs & Services → Library).
2. IAM & Admin → Service Accounts → Create Service Account (e.g.
   `github-actions-play-release`). No project role needed - permissions
   are granted inside Play Console instead (next step).
3. Open the new service account → Keys → Add Key → Create new key → JSON.
   This downloads a `.json` file - that whole file's contents are the
   `PLAY_STORE_SERVICE_ACCOUNT_JSON` secret below.
4. In Play Console → Users and permissions → Invite new users, paste the
   service account's email (looks like
   `github-actions-play-release@<project-id>.iam.gserviceaccount.com`).
   Grant at least: **Release apps to testing tracks**, and **Release apps
   to production, exclude, and other tracks** if you'll ever use this
   workflow's alpha/beta/production options.

### 4. Add these as GitHub repo secrets

Repo → Settings → Secrets and variables → Actions → New repository
secret. All five are required; the workflow checks for them up front and
fails with a clear message naming whichever are missing.

| Secret | Value |
| --- | --- |
| `PLAY_STORE_KEYSTORE_BASE64` | `base64 -w0 play-store-release.keystore` (Windows: `certutil -encode play-store-release.keystore tmp.b64` then strip the header/footer lines) - the whole base64 string, one line |
| `PLAY_STORE_KEYSTORE_PASSWORD` | the keystore password from step 1 |
| `PLAY_STORE_KEY_ALIAS` | `opentouch-play` (or whatever `-alias` you used) |
| `PLAY_STORE_KEY_PASSWORD` | the key password from step 1 |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | the entire contents of the JSON file from step 3, pasted as-is |

Once all five exist, run the workflow once by hand (Actions tab → Publish
to Google Play → Run workflow → track: internal) before ever relying on
the tag-push trigger, to confirm the whole chain works end to end.
