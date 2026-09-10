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

The Android app does not bundle model files in the APK. At runtime it creates
an app-owned model directory at:

```text
Android/data/com.opentouch.sensorapp/files/models/
```

The AI menu's **Import model files** action copies selected files into that
directory. Each ONNX model needs a matching JSON configuration file with the
same base name, for example:

- `key_finger.onnx` - trained two-class model
- `key_finger.json` - input size, RGB normalization, and output labels

The key/finger JSON configuration is generated beside the model when the
model is imported. Downloaded models can use the same directory and naming
convention. This keeps large model files independent from APK releases and
avoids storage permissions because the directory is app-owned.

For a more comfortable one-download flow, package the files as
`<model-name>.opentouchmodel`. This is a ZIP-based OpenTouch package containing
`model.onnx` and, for models other than key/finger, `model.json`. The app is
registered as an Android file handler for this extension, so tapping the
download can open OpenTouch and extract the files automatically. Create a
package with:

```powershell
.\tools\package-opentouch-model.ps1 `
  -ModelPath .\model_key_finger\output\key_finger.onnx `
  -ConfigPath .\path\to\key_finger.json
```

For `key_finger.onnx`, `-ConfigPath` can be omitted because the app generates
the known key/finger configuration automatically.

ONNX Runtime is delivered separately in the `mlruntime` dynamic feature
module. The app requests this signed module on first launch; Google Play
downloads it once and keeps it installed for later launches. This requires
publishing and installing the app as an Android App Bundle. A standalone APK
installed outside Google Play cannot download an on-demand Play feature, so
the AI action remains unavailable in that case.

Selecting a model in the AI menu starts live analysis immediately. The sensor
preview keeps running, and the result shows the latest class, confidence, and
processing time. Select **None** or tap **Photo** to stop analysis and return to capture.
There is no need to freeze or save an image first.

`LiveModelAnalyzer.kt` processes frames on a dedicated background thread.
The camera's GL capture thread samples up to five frames per second (one every
200 ms) and keeps only the newest waiting frame. If inference is slower, older
frames are dropped; results update whenever processing finishes. Bitmaps are
released after each run, and the session closes after pending inference finishes
when changing models, disconnecting the sensor, or backgrounding the app.
Debug and release runners use at most two intra-op CPU threads for inference.

The current model was trained with very few
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

For the Play Store build, use an Android App Bundle so the on-demand ML
feature is included in the published package:

```powershell
.\gradlew.bat bundleRelease
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
