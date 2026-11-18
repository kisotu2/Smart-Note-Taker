# SmartNote Android Studio Project

## Build toolchain
This project uses a deliberately compatible Android toolchain rather than AGP 9.x, which can break legacy Android Gradle plugin APIs used by some plugins:

- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.2.21
- Kotlin Compose plugin 2.2.21
- KSP 2.2.21-2.0.4
- Room 2.8.5 with KSP (not KAPT)
- Compile/target SDK 36; minimum SDK 24
- JDK 17

## Open and run
1. Extract the ZIP and open the `SmartNote` directory in Android Studio.
2. Set Gradle JDK to JDK 17.
3. Install Android SDK Platform 36 using SDK Manager.
4. Sync Gradle and then Build > Rebuild Project.

If the Gradle wrapper JAR is absent in this archive, use Android Studio's Gradle sync after opening the project or regenerate the wrapper using a local Gradle 8.13 installation: `gradle wrapper --gradle-version 8.13`. Then run `./gradlew assembleDebug` on macOS/Linux.

## Features
Native Kotlin + Jetpack Compose screens, local notes storage, search/favorites, OCR integration, study tools and local summarization fallback. Authentication is a local/demo flow, not a production online identity service.

## Note
This archive's build files have been updated for compatibility, but a full Android build has not been executed in this environment. Android Studio must download dependencies and the Android SDK to validate the build.
