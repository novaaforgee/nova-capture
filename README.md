# Nova Capture – Professional Android Screenshot & Screen Recorder

An ultra-sleek, offline-first Android application built with **Kotlin**, **Jetpack Compose (Material 3)**, and **MediaProjection API**.

Inspired by discreet screenshot tools, but custom-engineered with:
- **Instant Public Gallery Saving:** Screenshots save straight into `DCIM/Screenshots` and videos into `Movies/NovaCapture`.
- **Presentation / Stealth Mode:** Launches into a clean dashboard and collapses into a draggable, high-contrast floating pill widget with both **Photo Screenshot** and **Screen Video Recording** triggers.
- **Zero Cloud / 100% Offline:** Completely local, private, and secure.

---

## 📱 Features

1. **Floating Capture Widget:**
   - **Camera Icon:** Single tap captures screen immediately (temporarily auto-hides the widget for a clean capture) and saves directly to Public Gallery.
   - **Video Shutter:** One-touch screen recording toggle with live status indicator.
   - **Close Button:** Dismisses foreground service and returns control.
   - **Draggable:** Position anywhere on screen.

2. **Presentation Dashboard:**
   - Real-time service status toggle.
   - Overview of public gallery folders (`DCIM/Screenshots`, `Movies/NovaCapture`).
   - Integrated gallery browser with image preview, direct system share, and delete.

3. **Android Security & FLAG_SECURE Compliance:**
   - Uses standard official `MediaProjection` with explicit user consent.
   - Strictly abides by Android OS security boundaries (banking and DRM-protected views with `FLAG_SECURE` will render blank/black, avoiding system policy violations).

---

## 🔒 Permissions Used

- `SYSTEM_ALERT_WINDOW`: For floating shutter widget on top of all screens.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PROJECTION`: Continuous authorized capture session.
- `RECORD_AUDIO` (Optional): Internal/mic audio recording for screen video.
- `VIBRATE`: Haptic confirmation on capture.

---

## 🚀 Automated GitHub Actions CI/CD

Every commit to `main` automatically triggers `.github/workflows/build-apk.yml`, compiling the project using Gradle and publishing a ready-to-install debug APK as an artifact.
