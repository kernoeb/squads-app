# Squads Android — development commands

# Default: list available recipes
default:
    @just --list

# ─── Build ────────────────────────────────────────────────────

# Build debug APK
build:
    ./gradlew assembleDebug

# Build release APK
release:
    ./gradlew assembleRelease

# Clean build artifacts
clean:
    ./gradlew clean

# Full clean rebuild
rebuild: clean build

# ─── Run ──────────────────────────────────────────────────────

# Install debug APK on connected device
install:
    ./gradlew installDebug

# Build + install + launch on device
run: install
    adb shell am start -n com.squads.app/.MainActivity

# ─── Quality ──────────────────────────────────────────────────

# Run lint checks
lint:
    ./gradlew lintDebug

# Run ktlint code style check
ktlint:
    ./gradlew ktlintCheck

# Auto-format code with ktlint
format:
    ./gradlew ktlintFormat

# Run unit tests
test:
    ./gradlew testDebugUnitTest

# Run instrumented tests (requires device/emulator)
test-device:
    ./gradlew connectedDebugAndroidTest

# Run all Maestro UI tests (requires device/emulator)
maestro:
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true maestro test .maestro/

# Run a single Maestro flow (e.g., just maestro-one demo-login)
maestro-one flow:
    MAESTRO_CLI_NO_ANALYTICS=1 MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true maestro test .maestro/{{flow}}.yaml

# ─── Info ─────────────────────────────────────────────────────

# Show project dependencies
deps:
    ./gradlew app:dependencies --configuration debugRuntimeClasspath

# Show connected devices
devices:
    adb devices -l

# Print APK size after build
apk-size: build
    @du -h app/build/outputs/apk/debug/app-debug.apk 2>/dev/null || echo "No APK found"

# ─── Shortcuts ────────────────────────────────────────────────

# Open logcat filtered to Squads
logcat:
    adb logcat --pid=$(adb shell pidof -s com.squads.app) 2>/dev/null || adb logcat | grep -i squads

# Kill the app on device
kill:
    adb shell am force-stop com.squads.app

# Restart: kill + run
restart: kill run
