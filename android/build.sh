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

echo "=== Loading fixed PUBLIC signing key ==="
PUBLIC_KS=$PROJECT/signing/zhangxinchuang-public-release.p12
PUBLIC_KS_PASSWORD=${PUBLIC_KS_PASSWORD:-zhangxinchuang-public-30600}
if [ ! -f "$PUBLIC_KS" ]; then
    echo "Public release keystore not found: $PUBLIC_KS"
    exit 1
fi

echo "=== Aligning ==="
$BUILD_TOOLS/zipalign -f 4 app.unsigned.apk app.aligned.apk

echo "=== Signing public APK ==="
$BUILD_TOOLS/apksigner sign \
    --ks "$PUBLIC_KS" \
    --ks-type PKCS12 \
    --ks-pass pass:"$PUBLIC_KS_PASSWORD" \
    --key-pass pass:"$PUBLIC_KS_PASSWORD" \
    --ks-key-alias zhangxinchuang-public \
    --out "$PROJECT/Zhangxinchuang-public-v0.3.7.apk" \
    app.aligned.apk

echo "=== Verifying fixed public signature ==="
VERIFY_OUTPUT=$($BUILD_TOOLS/apksigner verify --verbose --print-certs "$PROJECT/Zhangxinchuang-public-v0.3.7.apk")
echo "$VERIFY_OUTPUT"
echo "$VERIFY_OUTPUT" | grep -qi "aea75c9b2b5f5c42d56b72d4a69a79a38e1c57f27db021017be8656bc8f002fb"

echo ""
echo "=== Done ==="
echo "APK: $PROJECT/Zhangxinchuang-public-v0.3.7.apk"
ls -lh "$PROJECT/Zhangxinchuang-public-v0.3.7.apk"
