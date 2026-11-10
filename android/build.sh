#!/usr/bin/env bash
# Builds island.apk without Gradle (aapt2 + javac + d8 + apksigner).
set -euo pipefail
SDK=${ANDROID_SDK:-/root/opt/android/sdk}
BT=$SDK/build-tools/34.0.0
PLAT=$SDK/platforms/android-34/android.jar
cd "$(dirname "$0")"

rm -rf build && mkdir -p build/classes build/dex build/gen

# Prefer tools on PATH: the SDK's x86_64 binaries cannot run on this ARM phone.
AAPT2=$(command -v aapt2 || true)
[ -n "$AAPT2" ] || AAPT2=$BT/aapt2
ZIPALIGN=$(command -v zipalign || true)
[ -n "$ZIPALIGN" ] || ZIPALIGN=$BT/zipalign

"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/base.apk -I "$PLAT" --manifest AndroidManifest.xml build/res.zip \
    --java build/gen --min-sdk-version 26 --target-sdk-version 34

javac --release 11 -nowarn -Xlint:-options -cp "$PLAT" -d build/classes \
    $(find src build/gen -name '*.java')
find build/classes -name '*.class' > build/classlist.txt
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --lib "$PLAT" --min-api 26 \
    --output build/dex $(cat build/classlist.txt)

(cd build/dex && zip -q ../base.apk classes.dex)
"$ZIPALIGN" -f -p 4 build/base.apk build/aligned.apk

KS=island.keystore
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass pass:android \
    --key-pass pass:android --out island.apk build/aligned.apk
java -jar "$BT/lib/apksigner.jar" verify --print-certs island.apk | head -2
ls -lh island.apk
