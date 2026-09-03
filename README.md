# OpenTouch Mobile

Android application for working with an OpenTouch USB sensor/camera device.

The app connects to a supported USB UVC sensor, shows the live camera preview, and lets the user capture photos or videos. It is a standalone Android app: there is no backend server, database, or external API required for normal use.

<p align="center">
  <a href="https://github.com/lasr-lab/opentouchapp/releases/latest/download/OpenTouch-latest.apk">
    <img src="https://img.shields.io/badge/Download-latest%20APK-brightgreen?style=for-the-badge" alt="Download latest APK">
  </a>
</p>

## Features

- USB sensor detection and permission handling
- Supported-sensor confirmation popup
- Live camera preview with sensor-oriented rotation
- Sensor-shaped preview crop
- Photo capture saved to the device gallery
- Video recording saved to the device gallery
- Gallery shortcut
- RGB illumination controls
- On-device key/finger image classification

## On-Device ML Model

The current classifier was trained in PyTorch using images from
`model_key_finger/dataset/key` and `model_key_finger/dataset/finger`. The
trained model is exported to the portable ONNX format by
`model_key_finger/export_model.py` so it can run directly on Android without
a server.

The Android app bundles these files in `app/src/main/assets/models/`:

- `key_finger.onnx` - trained two-class model
- `labels.txt` - output labels: `key` and `finger`
- `model_config.json` - input size and RGB normalization settings

When the user captures an image and selects Analyze, `ModelRunner.kt` resizes
and normalizes the image, runs it with ONNX Runtime, and displays the class
with the highest probability. The current model was trained with very few
images, so its predictions are intended for workflow testing rather than
reliable classification.

## Project Structure

- `app` - main Android application, UI, permissions, capture flow, and sensor UX
- `libausbc` - high-level USB camera framework and rendering/capture bridge
- `libuvc` - low-level USB/UVC camera communication and native code
- `libnative` - native helper code for image/audio processing utilities

## Requirements

- Android Studio
- JDK 21
- Android SDK with API 36
- Android NDK `27.0.12077973`
- CMake `3.10.2`

## Build Locally

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated under:

```text
app/build/outputs/apk/debug/
```

To build a release APK locally:

```powershell
.\gradlew.bat :app:assembleRelease
```

## CI And Releases

Pull requests run the Android CI workflow and build a debug APK artifact.

Versioned GitHub releases are created from tags:

```powershell
git switch main
git pull origin main
git tag v1.1.3
git push origin v1.1.3
```

When a `v*` tag is pushed, GitHub Actions builds the release APK and attaches it to the release as `OpenTouch-v<version>.apk` plus a stable `OpenTouch-latest.apk` download asset.

## Documentation

More project details are in [docs/PROJECT_DOCUMENTATION.md](docs/PROJECT_DOCUMENTATION.md).
