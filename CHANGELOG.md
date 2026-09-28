# Changelog

## 1.1.15

- Model packages are now a normal `.zip` with `model.onnx` and `model.json`.
  `.opentouchmodel` is no longer used.
- The AI menu can download `key_finger` and `max_model` from
  https://opentouch.org/mobile/ without leaving the app. After the download
  finishes, tap the model name in the AI menu to start live analysis.
- Opening a model zip from Downloads still imports it into the app.
- Photo captures store AI results in EXIF only. Videos still burn in the
  overlay and keep the recording metadata.
- Play Store package id is `com.opentouch.android`.
- Removed the old `.opentouchmodel` packager (`model_converter`).

## 1.1.14

- Added live ONNX model inference while the sensor preview continues running.
- Capture and model processing use separate threads; the screen updates when
  each completed result is available without freezing the preview.
- Added model import/delete handling with required JSON configuration files.
- Added the standalone `model_max_model` training/export workflow and generated
  `max_model.opentouchmodel` with nine texture classes.
- Improved model-loading diagnostics and model cleanup when changing screens or
  models.
