# Phase 5 Implementation Plan — Release Packaging, ProGuard/R8 & CI/CD

## 1. Overview & Objective
Prepare the Wii U GamePad Android client and Cemu fork for standalone distribution and open-source release:
1. **ProGuard / R8 Optimization**: Ensure bytecode minification and dead-code stripping do not break DSU binary reflection, Kotlin Coroutines, Jetpack Compose, or MediaCodec hardware buffers.
2. **Release Keystore Signing**: Configure reproducible APK release signing via Gradle properties or environment variables.
3. **GitHub Actions CI/CD**: Create an automated pipeline building both the Android APK and the Cemu Windows binary on every tag or push to `main`.
4. **Upstream PR Readiness**: Structure Cemu changes cleanly under preprocessor guards (`#ifdef ENABLE_GAMEPAD_STREAMING` or clean module boundaries) for seamless upstream merging.

---

## 2. Implementation Checklist & Step-by-Step Code Modifications

- [ ] **Step 2.1: Configure ProGuard / R8 Keep Rules**
  - [ ] Create `android-gamepad-app/app/proguard-rules.pro`.
  - [ ] Preserve DSU packet reflection, coroutines, and MediaCodec hardware buffers from stripping.

#### [NEW] [`android-gamepad-app/app/proguard-rules.pro`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/proguard-rules.pro)
```proguard
# Jetpack Compose Rules
-keepattributes *Annotation*
-dontwarn androidx.compose.**

# Keep DSU Protocol Data Classes & Enums (serialized over network)
-keep class com.cemupad.dsu.** { *; }
-keepclassmembers class com.cemupad.dsu.** { *; }
-keep class com.cemupad.config.** { *; }

# Keep Network & Discovery models
-keep class com.cemupad.network.** { *; }

# Keep MediaCodec & AudioTrack hardware buffer structures
-keep class android.media.** { *; }
```

- [ ] **Step 2.2: Enable Release Minification in build.gradle.kts**
  - [ ] Enable `isMinifyEnabled = true` and `isShrinkResources = true` in `release` build type.
  - [ ] Assign ProGuard rules file.

#### [MODIFY] [`android-gamepad-app/app/build.gradle.kts`](file:///c:/Projects/wiiu-gamepad-android/android-gamepad-app/app/build.gradle.kts)
Enable minification in `release` build type:
```kotlin
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug") // fallback or release config
        }
        debug {
            isMinifyEnabled = false
        }
    }
```

- [ ] **Step 2.3: Configure Automated CI/CD Workflow**
  - [ ] Create `.github/workflows/build-artifacts.yml`.
  - [ ] Define automated build job for Android APK (JDK 17).
  - [ ] Define automated build job for Cemu Windows x64 binary (MSVC + CMake).

#### [NEW] [`.github/workflows/build-artifacts.yml`](file:///c:/Projects/wiiu-gamepad-android/.github/workflows/build-artifacts.yml)
GitHub Actions workflow compiling the Android APK and Cemu Windows binary:
```yaml
name: Build & Package Artifacts

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build-android:
    name: Build Android APK
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Grant execute permission for gradlew
        run: chmod +x android-gamepad-app/gradlew
      - name: Build Debug & Release APK
        run: |
          cd android-gamepad-app
          ./gradlew testDebugUnitTest assembleDebug assembleRelease
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: cemupad-android-apk
          path: android-gamepad-app/app/build/outputs/apk/**/*.apk

  build-cemu:
    name: Build Cemu Windows x64
    runs-on: windows-2022
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive
      - name: Set up MSVC & CMake
        uses: ilammy/msvc-dev-cmd@v1
      - name: Configure CMake
        run: |
          cmake -B Cemu/build -S Cemu -DCMAKE_BUILD_TYPE=Release
      - name: Build Cemu Target
        run: |
          cmake --build Cemu/build --config Release --target Cemu
      - name: Upload Cemu Executable
        uses: actions/upload-artifact@v4
        with:
          name: Cemu-Streaming-Windows-x64
          path: Cemu/build/bin/Release/Cemu.exe
```

---

## 3. Verification & Testing Checklist

- [ ] **3.1 Release APK Compilation & Minification Verification**
  - [ ] Execute release build with R8 optimization:
    ```powershell
    cd c:\Projects\wiiu-gamepad-android\android-gamepad-app
    .\gradlew.bat testReleaseUnitTest assembleRelease
    ```
  - [ ] Confirm output file exists at `app/build/outputs/apk/release/app-release-unsigned.apk`.
- [ ] **3.2 Device Smoke Testing**
  - [ ] Install and launch on physical device:
    ```powershell
    adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
    adb shell am start -n com.cemupad/.MainActivity
    ```
  - [ ] Verify app launches cleanly without R8 ClassNotFoundExceptions or missing Compose reflection crashes.
