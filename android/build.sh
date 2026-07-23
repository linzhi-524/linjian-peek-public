#!/bin/bash
set -e

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/android-sdk}
PLATFORM=$ANDROID_HOME/platforms/android-34/android.jar
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0

PROJECT="$(cd "$(dirname "$0")" && pwd)"
SRC=$PROJECT/app/src/main
OUT=$PROJECT/build
PKG_PATH=dev/linjian/peek

if [ ! -f "$PLATFORM" ]; then
  echo "Android platform not found: $PLATFORM"
  echo "Install Android SDK platform 34 first."
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/apk" "$OUT/compiled_res"

echo "=== Compiling resources ==="
$BUILD_TOOLS/aapt2 compile --dir "$SRC/res" -o "$OUT/compiled_res/"

echo "=== Linking resources ==="
$BUILD_TOOLS/aapt2 link \
    -o "$OUT/apk/app.unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$SRC/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --auto-add-overlay \
    -R "$OUT/compiled_res"/*.flat

echo "=== Compiling Java ==="
find "$SRC/java" -name "*.java" > "$OUT/sources.txt"
echo "$OUT/gen/$PKG_PATH/R.java" >> "$OUT/sources.txt"
javac -encoding UTF-8 -source 11 -target 11 -classpath "$PLATFORM" -d "$OUT/classes" @"$OUT/sources.txt"

echo "=== Creating DEX ==="
$BUILD_TOOLS/d8 --output "$OUT/apk/" --lib "$PLATFORM" $(find "$OUT/classes" -name "*.class")

echo "=== Building APK ==="
cd "$OUT/apk"
cp app.unsigned.apk app.tmp.apk
zip -d app.tmp.apk classes.dex 2>/dev/null || true
zip -j app.tmp.apk classes.dex
mv app.tmp.apk app.unsigned.apk

echo "=== Generating DEBUG signing key ==="
DEBUG_KS=$OUT/debug.jks
if [ ! -f "$DEBUG_KS" ]; then
    keytool -genkeypair -v \
        -keystore "$DEBUG_KS" \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -alias linjian-peek \
        -storepass linjian-debug \
        -keypass linjian-debug \
        -dname "CN=Zhangxinchuang Public v0.3.4.1 Debug"
fi

echo "=== Aligning ==="
$BUILD_TOOLS/zipalign -f 4 app.unsigned.apk app.aligned.apk

echo "=== Signing debug APK ==="
$BUILD_TOOLS/apksigner sign \
    --ks "$DEBUG_KS" \
    --ks-pass pass:linjian-debug \
    --key-pass pass:linjian-debug \
    --ks-key-alias linjian-peek \
    --out "$PROJECT/Zhangxinchuang-public-v0.3.4.1.apk" \
    app.aligned.apk

echo ""
echo "=== Done ==="
echo "APK: $PROJECT/Zhangxinchuang-public-v0.3.4.1.apk"
ls -lh "$PROJECT/Zhangxinchuang-public-v0.3.4.1.apk"
