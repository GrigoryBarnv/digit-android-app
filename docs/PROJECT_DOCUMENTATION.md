# Project Documentation

## Overview

Open Touch is an Android app for a USB tactile/sensor camera device. It is based on an Android USB camera stack and has been adapted for the Open Touch workflow: connect a supported sensor, preview the camera image, capture photos or videos, and prepare the UI for future AI model integration.

The app is fully local. It does not use a backend service, remote database, or web API during normal operation.

## Main User Flow

1. The user opens the app.
2. The app requests Android camera permission if needed.
3. The user connects a USB sensor.
4. Android asks for USB device access.
5. The app checks whether the connected sensor is supported.
6. The live preview opens.
7. The user captures a photo, records a video, changes RGB illumination, opens the gallery, or selects an AI placeholder option.

## Architecture

The project is a multi-module Gradle Android project.

`app`

Main application module. It contains the Compose UI, activities, camera preview fragment, permission flow, capture/video logic, gallery shortcut, RGB controls, supported-sensor popup, and current app branding.

`libausbc`

High-level USB camera layer. It hides most camera connection complexity and provides APIs for preview, capture, recording, rendering, and camera lifecycle handling.

`libuvc`

Low-level USB/UVC layer. It communicates with the physical USB camera through native code and wraps device access for the higher layers.

`libnative`

Native helper module for performance-sensitive media utilities.

## Important App Files

- `app/src/main/java/com/opentouch/sensorapp/presentation/screen/DemoScreen.kt` - main camera screen UI
- `app/src/main/java/com/opentouch/sensorapp/presentation/fragment/CameraPreviewFragment.kt` - USB camera connection, preview, capture, video, and save logic
- `app/src/main/java/com/opentouch/sensorapp/data/SupportedSensors.kt` - list of supported sensor vendor/product IDs
- `app/src/main/java/com/opentouch/sensorapp/presentation/component/RgbControls.kt` - RGB illumination controls
- `app/src/main/java/com/opentouch/sensorapp/presentation/component/SensorPreviewShape.kt` - sensor-shaped preview outline

## Build

Debug build:

```powershell
.\gradlew.bat :app:assembleDebug
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

CI uses Ubuntu, JDK 21, Android SDK setup, CMake `3.10.2`, and Gradle.

## Release Process

The release workflow runs when a Git tag starting with `v` is pushed.

Example:

```powershell
git switch main
git pull origin main
git tag v1.1.0
git push origin v1.1.0
```

GitHub Actions then:

1. checks out the code,
2. installs Java and Android build tools,
3. builds `:app:assembleRelease`,
4. renames the APK to `app-release-unsigned.apk`,
5. creates a GitHub Release and attaches the APK.

## Adding A Supported Sensor

Supported sensors are defined in `SupportedSensors.kt`.

To add a sensor:

1. connect the sensor to a device,
2. read the detected Vendor ID and Product ID from the connection popup/logs,
3. add a new `SupportedSensor(...)` entry,
4. rebuild and test the connection flow.

## Current Roadmap

- Make FPS and resolution user-controllable.
- Add real on-device AI model installation and selection.
- Add settings screen for sensor metadata and technical options.
- Improve support for multiple sensor versions through external configuration.
- Continue UI testing on different Android screen sizes.
