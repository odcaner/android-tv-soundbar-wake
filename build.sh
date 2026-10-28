#!/usr/bin/env bash
# Builds soundbar-wake.apk without Gradle.
# Needs: JDK 17+, Android SDK with build-tools and a platform (ANDROID_HOME).
# Signing: set KEYSTORE / KS_PASS, or a debug key is created in ./debug.keystore.
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
PLAT="$(ls -d "$SDK"/platforms/* | sort -V | tail -1)/android.jar"
KEYSTORE="${KEYSTORE:-debug.keystore}"
KS_PASS="${KS_PASS:-android}"

rm -rf build && mkdir -p build/classes build/dex
"$BT/aapt2" link -o build/base.apk -I "$PLAT" --manifest AndroidManifest.xml
javac --release 8 -classpath "$PLAT" -d build/classes $(find src -name '*.java')
"$BT/d8" --min-api 26 --lib "$PLAT" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Soundbar Wake"
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --out soundbar-wake.apk build/aligned.apk
echo "built: soundbar-wake.apk"
