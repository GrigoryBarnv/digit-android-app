# Changelog

## 1.1.14

- Added live ONNX model inference while the sensor preview continues running.
- Capture and model processing use separate threads; the screen updates when
  each completed result is available without freezing the preview.
- Added model import/delete handling with required JSON configuration files.
- Added the standalone `model_max_model` training/export workflow and generated
  `max_model.opentouchmodel` with nine texture classes.
- Improved model-loading diagnostics and model cleanup when changing screens or
  models.
