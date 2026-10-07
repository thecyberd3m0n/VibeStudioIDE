#!/bin/bash
set -e

echo "=== VibeStudio Android Instrumentation Test Suite (Shizuku) ==="

# Helper function to execute shizuku commands cleanly filtering SecurityException warnings
shk_run() {
    shizuku exec "$@" 2>&1 | grep -v "SecurityException" || true
}

# Helper function to install APK via Shizuku PackageInstaller stream session
shk_install_apk() {
    local apk_path="$1"
    shizuku exec "sh -c 'SIZE=\$(stat -c %s \"$apk_path\"); S=\$(pm install-create -S \$SIZE | cut -d \"[\" -f 2 | cut -d \"]\" -f 1); cat \"$apk_path\" | pm install-write -S \$SIZE \$S base.apk -; pm install-commit \$S'" 2>&1 | grep -v "SecurityException" || true
}

# Parse options
BUILD_ARGS=()
TEST_CLASS=""
TEST_METHOD=""
SKIP_BUILD=false
RESET_DATA=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        -class|-c|--class)
            TEST_CLASS="$2"
            shift 2
            ;;
        -method|-m|--method)
            TEST_METHOD="$2"
            shift 2
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --reset|--clear)
            RESET_DATA=true
            shift
            ;;
        *)
            BUILD_ARGS+=("$1")
            shift
            ;;
    esac
done

# 1. Check Shizuku Service Status
echo "=== Step 1: Checking Shizuku ADB Connection ==="
if ! command -v shizuku &> /dev/null; then
    echo "ERROR: termux-shizuku-tools is not installed. Run: pkg install termux-shizuku-tools"
    exit 1
fi

SHIZUKU_ID=$(shk_run "id" | grep "uid=" || true)
if [ -z "$SHIZUKU_ID" ]; then
    echo "ERROR: Shizuku service is not running or permission denied."
    echo "Please start Shizuku service on host device."
    exit 1
fi

echo "Shizuku ADB session verified: $SHIZUKU_ID"

cleanup() {
    echo "=== Cleaning up: Ensuring screen remains awake and unlocked ==="
    shk_run "input keyevent 224"
    shk_run "wm dismiss-keyguard"
}
trap cleanup EXIT INT TERM

echo "=== Preventing Screen Lock during Test Run ==="
shk_run "settings put system screen_off_timeout 2147483647"
shk_run "settings put global stay_on_while_plugged_in 15"
shk_run "settings put secure lockscreen.disabled 1"
shk_run "input keyevent 224"
shk_run "wm dismiss-keyguard"

# Reset app data if requested
if [ "$RESET_DATA" = true ]; then
    echo "=== Resetting App Data (pm clear com.vibestudio.app) ==="
    shk_run "pm clear com.vibestudio.app"
fi

# 2. Build Target APK and Instrumentation Test APK
if [ "$SKIP_BUILD" = false ]; then
    echo "=== Step 2: Building Target & Test APKs ==="
    ./build_test_apk.sh "${BUILD_ARGS[@]}"

    # 3. Ensure target app & test app are installed on device
    echo "=== Step 3: Installing APKs on Host Device via Shizuku ==="
    cp bin/VibeStudio.apk /sdcard/VibeStudio.apk
    echo "Installing VibeStudio.apk..."
    shk_install_apk /sdcard/VibeStudio.apk

    cp bin/VibeStudioTest.apk /sdcard/VibeStudioTest.apk
    echo "Installing VibeStudioTest.apk..."
    shk_install_apk /sdcard/VibeStudioTest.apk
else
    echo "=== Skipping Build & Install (--skip-build specified) ==="
fi

# 4. Execute Instrumentation Tests via Android Framework (am instrument)
echo "=== Step 4: Launching Android Framework Instrumentation Tests ==="
TEST_RUNNER="com.vibestudio.app.test/androidx.test.runner.AndroidJUnitRunner"

EXTRA_TEST_ARGS=""
if [ -n "$TEST_CLASS" ] && [ -n "$TEST_METHOD" ]; then
    EXTRA_TEST_ARGS="-e class ${TEST_CLASS}#${TEST_METHOD}"
    echo "Targeting test method: ${TEST_CLASS}#${TEST_METHOD}"
elif [ -n "$TEST_CLASS" ]; then
    EXTRA_TEST_ARGS="-e class ${TEST_CLASS}"
    echo "Targeting test class or method: ${TEST_CLASS}"
fi

echo "Executing: am instrument $EXTRA_TEST_ARGS -w $TEST_RUNNER"
shk_run "am instrument $EXTRA_TEST_ARGS -w $TEST_RUNNER"
