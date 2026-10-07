#!/bin/bash
set -e

echo "=== VibeStudio Build Target & Test APK ==="

# 1. First build the target app APK if missing or if --rebuild specified
if [ ! -f "bin/VibeStudio.apk" ] || [ "$1" == "--rebuild" ]; then
    ./build.sh
fi

# 2. Setup directories for Test APK
mkdir -p bin obj_test_apk gen_test_r compiled_test_res

# 3. Create Manifest for Test APK
cat << 'MANIFEST' > bin/TestAndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.vibestudio.app.test">

    <application>
        <uses-library android:name="android.test.runner" />
        <activity
            android:name="androidx.test.core.app.InstrumentationActivityInvoker$EmptyActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
        <activity
            android:name="androidx.test.core.app.InstrumentationActivityInvoker$EmptyFloatingActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
            </intent-filter>
        </activity>
    </application>

    <instrumentation
        android:name="androidx.test.runner.AndroidJUnitRunner"
        android:targetPackage="com.vibestudio.app"
        android:label="VibeStudio Android Instrumentation Tests" />
</manifest>
MANIFEST

# 4. Compile AndroidX Test Resources and Link Test APK Shell
echo "=== Packaging Test APK Shell ==="
rm -rf compiled_test_res gen_test_r
mkdir -p compiled_test_res gen_test_r

aapt2 link -o bin/test.unsigned.apk \
  -I libs/android.jar \
  --manifest bin/TestAndroidManifest.xml \
  --min-sdk-version 26 \
  --target-sdk-version 28 \
  --version-code 1 \
  --version-name 1.0 \
  --java gen_test_r

# 5. Build Classpath for Instrumentation Test compilation
TEST_COMPILE_CLASSPATH="libs/android.jar:obj:libs/libtermux.jar"
for j in libs/deps/*.jar; do
    TEST_COMPILE_CLASSPATH="$TEST_COMPILE_CLASSPATH:$j"
done

# 6. Compile AndroidTest Java Sources
echo "=== Compiling androidTest Java sources ==="
rm -rf obj_test_apk
mkdir -p obj_test_apk

javac -source 1.8 -target 1.8 -d obj_test_apk \
  -classpath "$TEST_COMPILE_CLASSPATH" \
  $(find app/src/androidTest/java -name "*.java")

# 7. Package Compiled Test Classes & Dependencies into Test DEX
echo "=== Converting Test Bytecode & Dependencies to DEX (d8) ==="
jar cvf bin/test_classes.jar -C obj_test_apk . > /dev/null

TEST_DEX_LIBS="bin/test_classes.jar"
# Include test runner, monitor, ext junit, hamcrest, etc.
for lib in runner-1.5.2.jar androidx-test-monitor-1.6.1.jar androidx-test-core-1.5.0.jar androidx-test-annotation-1.0.1.jar ext-junit-1.1.5.jar junit-4.13.2.jar hamcrest-core-1.3.jar androidx-test-storage-1.4.2.jar; do
    if [ -f "libs/deps/$lib" ]; then
        TEST_DEX_LIBS="$TEST_DEX_LIBS libs/deps/$lib"
    fi
done

rm -rf bin/test_dex
mkdir -p bin/test_dex

d8 --min-api 24 --lib libs/android.jar --output bin/test_dex $TEST_DEX_LIBS

# 8. Add classes.dex (and classes2.dex if multi-dex) into test.unsigned.apk
echo "=== Adding DEX to Test APK ==="
cd bin/test_dex
zip -r ../test.unsigned.apk *.dex > /dev/null
cd ../..

# 9. Sign and Align Test APK
echo "=== Signing & Aligning Test APK ==="
if [ ! -f debug.keystore ]; then
  keytool -genkey -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

jarsigner -keystore debug.keystore -storepass android -keypass android bin/test.unsigned.apk androiddebugkey > /dev/null

rm -f bin/test.aligned.apk
zipalign -v -p 4 bin/test.unsigned.apk bin/test.aligned.apk > /dev/null

apksigner sign --ks debug.keystore --ks-pass pass:android --min-sdk-version 26 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --out bin/VibeStudioTest.apk bin/test.aligned.apk

echo "=== Test APK Build Complete: bin/VibeStudioTest.apk ==="
