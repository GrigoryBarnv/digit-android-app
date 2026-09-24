# Play Store listing assets

Graphics required by the Google Play Console store-listing process. This
folder holds the source files we upload under Play Console → Grow users →
Store presence → Main store listing.

## Status

- **App icon**: placeholder source added (`icon/opentouch-icon-source-432x432.png`,
  pulled from the app's own adaptive-icon foreground layer). This is a
  starting point only - Play Console requires the icon as a **512x512,
  32-bit PNG with an alpha channel**, no baked-in rounded corners or
  drop shadow. The current file is 432x432 and has no alpha channel, so it
  needs to be re-exported/padded to spec before upload.
- **Feature graphic**: not created yet. Play Console requires **1024x500**,
  JPG or 24-bit PNG (no alpha).
- **Screenshots**: not added yet - to be captured once the app UI is
  finalized (per team decision, 2026-09-24). Folders are set up below so
  they just need to be dropped in.

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
