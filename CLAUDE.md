# CLAUDE.md and AGENTS.md

This file provides guidance when working with code in this repository.

## Project Overview

BattleZone is a territory control game similar to Risk or Dice Wars where players compete to control all territories on a board by rolling dice to attack adjacent territories.

The `dicewarsjs` folder contains legacy javascript code from Dice Wars. It is for reference only, no code in this folder should be modified.

The `BattleZone` folder contains the code for a kotlin multiplatform game that is under development.

Write idiomatic Kotlin code, and prefer a functional style where it makes sense.

## Agent instructions

Ask before commiting to  git.
Use symbolic constants instead of magic numbers. Especially for colors.
Always run all tests (`./gradlew test`) at the end of any code updates.

## Architecture

The **Kotlin Multiplatform (KMP)** project is located in `/BattleZone/` and has three main modules:

### Module Structure

- **`/composeApp`** - Compose Multiplatform UI application
  - `commonMain`: Shared UI code for all platforms (App.kt contains the main Composable)
  - `androidMain`: Android-specific entry point (MainActivity.kt)
  - `iosMain`: iOS-specific entry point (MainViewController.kt)
  - `jvmMain`: Desktop/JVM entry point
  - `webMain`: Web entry points (both JS and WASM targets)
  - Depends on `shared` module for common business logic

- **`/server`** - Ktor backend server (JVM only)
  - Main entry point: `Application.kt`
  - Runs on port 8080 (defined in shared/Constants.kt)
  - Depends on `shared` module

- **`/shared`** - Shared business logic library
  - Platform-agnostic code in `commonMain`
  - Platform-specific implementations in `androidMain`, `iosMain`, `jvmMain`, `jsMain`, `wasmJsMain`
  - Contains shared constants like SERVER_PORT

### Platform Targets

The project compiles to:
- Android (minSdk 24, targetSdk 36)
- iOS (Arm64 and Simulator Arm64)
- Desktop/JVM
- Web (both legacy JS and modern WASM targets)

## Build Commands

All commands must be run from the `BattleZone` directory.

### Build Applications
```bash
# Android
./gradlew :composeApp:assembleDebug

# Desktop (JVM)
./gradlew :composeApp:run

# Server
./gradlew :server:run

# Web (WASM - faster, modern browsers)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Web (JS - slower, legacy browser support)
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :shared:test
./gradlew :composeApp:test
./gradlew :server:test

# Run tests for specific platform
./gradlew :shared:jvmTest
./gradlew :shared:androidUnitTest
```
### Other Commands
```bash
# Clean build
./gradlew clean

# Build all variants
./gradlew build

# Check for dependency updates
./gradlew dependencyUpdates
```

## Technology Stack

- **Language**: Kotlin 2.3.0
- **UI Framework**: Compose Multiplatform 1.9.3 (with Material3)
- **Server**: Ktor 3.3.3 (Netty engine)
- **Build System**: Gradle with Kotlin DSL
- **Hot Reload**: Compose Hot Reload plugin enabled
- **Dependency Management**: Version catalog (`gradle/libs.versions.toml`)

## Code Organization

- Package namespace: `com.franklinharper.battlezone`
- Resources are generated in `battlezone.composeapp.generated.resources`
- The project uses typesafe project accessors (enabled in settings.gradle.kts)
- Java compatibility: Java 21

## Development Notes

- iOS builds require Xcode (open `/app/BattleZone/iosApp` directory)
- The project uses Compose Hot Reload for faster UI development iteration
- Common code should be placed in `commonMain` when possible; use platform-specific folders only when necessary
- The server and composeApp both depend on the shared module for common constants and business logic
