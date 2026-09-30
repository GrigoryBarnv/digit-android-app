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
