#!/bin/bash
set -e

echo "=== VibeStudio TDD Test Suite ==="

# 1. Ensure required directories exist
mkdir -p libs/deps bin obj obj_test gen_r compiled_res build/extracted_aars

# Check android.jar
if [ ! -f "libs/android.jar" ]; then
    echo "=== Downloading Android API 30 platform jar ==="
    curl -sL "https://dl.google.com/dl/android/repository/platform-30_r03.zip" -o libs/platform-30.zip
    unzip -q -j libs/platform-30.zip "android-11/android.jar" -d libs/
    rm -f libs/platform-30.zip
fi

# Resolve Maven dependencies if not present
if [ ! -f "libs/deps/junit-4.13.2.jar" ] || [ "$1" == "--rebuild-deps" ]; then
    echo "=== Resolving dependencies via Maven ==="
    mvn dependency:copy-dependencies -DoutputDirectory=libs/deps -q
fi

# Download AndroidX Test dependencies if missing
if [ ! -f "libs/deps/androidx-test-monitor-1.6.1.aar" ]; then
    echo "=== Downloading AndroidX Test AARs ==="
    curl -sL "https://dl.google.com/dl/android/maven2/androidx/test/monitor/1.6.1/monitor-1.6.1.aar" -o libs/deps/androidx-test-monitor-1.6.1.aar
    curl -sL "https://dl.google.com/dl/android/maven2/androidx/test/core/1.5.0/core-1.5.0.aar" -o libs/deps/androidx-test-core-1.5.0.aar
    curl -sL "https://dl.google.com/dl/android/maven2/androidx/test/annotation/1.0.1/annotation-1.0.1.aar" -o libs/deps/androidx-test-annotation-1.0.1.aar
    curl -sL "https://dl.google.com/dl/android/maven2/androidx/test/services/storage/1.4.2/storage-1.4.2.aar" -o libs/deps/androidx-test-storage-1.4.2.aar
fi

# 2. Extract AAR classes and collect packages for AAPT2
echo "=== Processing AAR dependencies ==="
EXTRA_PKGS=""
for aar in libs/deps/*.aar; do
    if [ -f "$aar" ]; then
        name=$(basename "$aar" .aar)
        mkdir -p "build/extracted_aars/${name}"
        unzip -q -o "$aar" -d "build/extracted_aars/${name}"
        if [ -f "build/extracted_aars/${name}/classes.jar" ]; then
            cp "build/extracted_aars/${name}/classes.jar" "libs/deps/${name}.jar" 2>/dev/null || true
        fi
        
        if [ -d "build/extracted_aars/${name}/res" ]; then
            if [ -f "build/extracted_aars/${name}/AndroidManifest.xml" ]; then
                pkg=$(grep -o 'package="[^"]*"' "build/extracted_aars/${name}/AndroidManifest.xml" | cut -d'"' -f2)
                if [ -n "$pkg" ]; then
                    EXTRA_PKGS="${EXTRA_PKGS}:${pkg}"
                fi
            fi
        fi
    fi
done
EXTRA_PKGS=${EXTRA_PKGS#:}

# Deduplicate conflicting jars
rm -f libs/deps/annotation-1.1.0.jar libs/deps/annotation-1.0.0.jar libs/deps/annotation-1.2.0.jar libs/deps/annotation-1.6.0.jar 2>/dev/null || true
rm -f libs/deps/kotlin-stdlib-jdk7-*.jar libs/deps/kotlin-stdlib-jdk8-*.jar libs/deps/kotlin-stdlib-common-*.jar libs/deps/listenablefuture-*.jar 2>/dev/null || true

# 3. Compile Android Resources (AAPT2)
echo "=== Compiling Android resources ==="
rm -f gen_r/com/vibestudio/app/R.java 2>/dev/null || true
mkdir -p compiled_res gen_r

aapt2 compile --dir app/src/main/res -o compiled_res/ > /dev/null

AAPT2_LINK_CMD="aapt2 link -o bin/app.unsigned.apk -I libs/android.jar --manifest app/src/main/AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 28 --version-code 1 --version-name 1.0 --replace-version --java gen_r --auto-add-overlay"
if [ -d "app/src/main/assets" ]; then
    AAPT2_LINK_CMD="$AAPT2_LINK_CMD -A app/src/main/assets"
fi
if [ -n "$EXTRA_PKGS" ]; then
    AAPT2_LINK_CMD="$AAPT2_LINK_CMD --extra-packages $EXTRA_PKGS"
fi

$AAPT2_LINK_CMD compiled_res/*.flat

# Build Classpath for app and test
TEST_CLASSPATH="obj:libs/android.jar:libs/libtermux.jar"
for j in libs/deps/*.jar; do
    TEST_CLASSPATH="$TEST_CLASSPATH:$j"
done

# 4. Compile VibeStudio Java sources
echo "=== Compiling VibeStudio app sources ==="
javac -source 1.8 -target 1.8 -d obj \
  -classpath "$TEST_CLASSPATH" \
  $(find gen_r -name "*.java") \
  $(find app/src/main/java -name "*.java")

# 5. Compile VibeStudio Test sources
echo "=== Compiling VibeStudio test sources ==="
javac -source 1.8 -target 1.8 -d obj_test \
  -classpath "$TEST_CLASSPATH:obj" \
  $(find app/src/test/java -name "*.java")

# Include assets, compiled apk, resources in test runtime classpath
FULL_RUNTIME_CLASSPATH="obj_test:obj:bin/app.unsigned.apk:app/src/main/res:app/src/main:libs/android.jar:libs/libtermux.jar"
for j in libs/deps/*.jar; do
    FULL_RUNTIME_CLASSPATH="$FULL_RUNTIME_CLASSPATH:$j"
done

# 6. Execute JUnit Test Runner
echo "=== Running Unit & UI Tests ==="
TEST_TARGETS=$(find app/src/test/java -name "*Test.java" | sed 's|app/src/test/java/||' | sed 's|\.java$||' | tr '/' '.')

if [ -z "$TEST_TARGETS" ]; then
    echo "No test classes found in app/src/test/java."
    exit 0
fi

echo "Running tests: $TEST_TARGETS"
java -Drobolectric.sqliteMode=LEGACY \
     -Drobolectric.conscryptMode=OFF \
     -Drobolectric.useConscrypt=false \
     -Drobolectric.logging=stdout \
     -Dandroid.package=com.vibestudio.app -Dandroid.manifest=app/src/main/AndroidManifest.xml -Dandroid.resources=app/src/main/res -Dandroid.assets=app/src/main/assets -Drobolectric.resourcesMode=legacy \
     -cp "$FULL_RUNTIME_CLASSPATH" \
     org.junit.runner.JUnitCore $TEST_TARGETS
