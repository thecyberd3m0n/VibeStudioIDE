# DEVELOPMENT_TERMUX.MD - VibeStudio Termux Development & Debugging Guide

This guide explains how to build, test, and run VibeStudio on Android via Termux in the simplest way.

---

## 1. Quick Start Commands (Simplest Way to Run)

- **Install dependencies (one-time setup)**:
  `./install_dependencies_termux.sh`
- **Build APK**:
  `./build.sh`
- **Run Unit Tests**:
  `./test.sh`
- **Run Instrumentation Tests**:
  `./test-shizuku.sh`
- **Run / Install APK (Simplest Way)**:
  `termux-open bin/VibeStudio.apk`

Executing `termux-open bin/VibeStudio.apk` opens the Android Package Installer directly from Termux, allowing you to install and launch the application immediately.

---

## 2. Testing Guidelines

- **Unit Tests**: Executed via `./test.sh` (unit test runner).
- **Instrumentation Tests**: Executed **only** via `./test-shizuku.sh`. Do not attempt to run instrumentation tests through other scripts or gradlew directly.

---

## 3. Debugging APK Installation Issues on Android

When Android Package Installer throws generic errors like *"Pakiet wygląda na nieprawidłowy"* (*Package appears to be invalid*), use the following steps to diagnose the exact system root cause:

### Step 1: Verify APK Structure & Signatures

1. **Verify Signatures (v1, v2, v3)**:
   `apksigner verify --verbose --min-sdk-version 24 bin/VibeStudio.apk`
   Ensure both **v1 (JAR signing)** and **v2/v3 (APK Signature Scheme)** are verified.

2. **Inspect APK Contents**:
   `unzip -l bin/VibeStudio.apk`
   CRITICAL: `classes.dex` MUST be located at the root level of the ZIP archive (`classes.dex`), NOT nested under a subdirectory like `bin/classes.dex`. Subdirectories cause PackageManager to throw `INSTALL_FAILED_INVALID_APK: Package code is missing`.

3. **Check Badging / Uses-SDK**:
   `aapt dump badging bin/VibeStudio.apk`
   Verify `sdkVersion` and `targetSdkVersion` match your device API level.

---

### Step 2: Common Installation Errors & Fixes

- **`INSTALL_FAILED_INVALID_APK: Package code is missing`**:
  `classes.dex` is missing or nested in a folder inside the APK.
- **`INSTALL_PARSE_FAILED_NO_CERTIFICATES`**:
  APK is missing v1/v2 signatures. Ensure `apksigner` is run on the aligned APK with `debug.keystore`.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**:
  An app with the same package name (`com.vibestudio.app`) was installed using a different signing key. Uninstall the previous app first.

---

## 4. Submodule & Architecture Rules

- **Do NOT modify submodules directly** (e.g., `libtermux-android`). All fixes, keyboard event translations, view wrappings, or custom behaviors MUST be implemented inside app workspace code (`app/src/...`), using custom subclasses, view wrappers, or delegating event listeners, without modifying external submodule libraries.
