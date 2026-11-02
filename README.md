# FileSharingApp for Android

Send photos to another phone **with nothing but a screen and a camera**. No Wi-Fi, Bluetooth,
NFC or internet. The sender's screen plays a looping stream of QR codes that carry the photos;
the receiver's camera reads them and rebuilds the photos.

This is the Android half of the project. The iPhone app lives in the
[FileSharingApp-iOS](https://github.com/Pratap-Abhang/FileSharingApp-iOS) repo. The two apps speak the same
protocol, so an iPhone can send to an Android phone and the other way round.

## How it works in 30 seconds

1. **Send**: pick photos. Each is shrunk to 1600 px and saved as WebP (about 100 to 300 KB).
2. The photos and a small manifest are packed into one *bundle*.
3. The bundle is cut into 1000-byte blocks. A **fountain code** turns the blocks into an endless
   stream of packets; each packet is one QR code. The screen shows about 10 per second.
4. **Receive**: the camera reads QR codes. Each valid packet brings the receiver closer to rebuilding
   the bundle. It does not matter which packets arrive, so it can start at any moment and miss frames.
5. When enough packets have arrived, the bundle is checked (SHA-256) and the photos are saved to
   `Pictures/FileSharingApp`.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and the wire format in
[protocol/spec.md](protocol/spec.md).

## Requirements

- Android Studio Ladybug (2024.2) or newer, with its bundled JDK 17+
- A phone running Android 10 (API 29) or newer. The emulator's virtual camera cannot show a QR
  stream well; use two real phones for receiving.

## Run on your Android phone

1. On the phone: *Settings > About phone*, tap **Build number** 7 times to unlock Developer options.
   Then *Settings > System > Developer options* and turn on **USB debugging**.
2. Connect the phone with a USB cable and accept the "Allow USB debugging?" prompt on the phone.
3. Open this folder (`android/`) in Android Studio and let Gradle sync. If Studio asks to install
   an Android SDK platform (API 35), accept.
4. Pick your phone in the device drop-down at the top and press **Run**. Or from a terminal:
   ```
   ./gradlew :app:installDebug
   ```
5. On phone A tap **Send photos** and choose photos. On phone B tap **Receive photos**, allow the
   camera, and point it at phone A.

The app has no launcher icon yet, so it shows the default Android icon.

## Run the tests

The codec is a plain Kotlin module in its own Gradle build, so its tests run without the Android SDK:

```
cd codec
../gradlew test
```

The tests compare every byte the Kotlin code produces (Base45 text, bundles, QR frames, random
numbers) with `protocol/test-vectors/vectors.json`, which the Python reference implementation in
`protocol/tools/reference.py` generated. If a test fails, the Kotlin code and the spec disagree.

To regenerate the vectors after a protocol change:

```
python3 protocol/tools/reference.py
```

and copy the updated `protocol/` folder to the iOS repo so both apps test against the same data.

## Project layout

```
android/
  protocol/            spec, Python reference, shared test vectors (same in the iOS repo)
  codec/               pure Kotlin: Base45, frames, fountain encoder/decoder, bundle (tested)
  app/                 the Android app (Jetpack Compose)
    send/              photo picker, compression, QR loop screen
    receive/           camera scanner, progress, saving to the gallery
  docs/                architecture and troubleshooting
```

## Permissions

Only the camera, used when receiving. The app has no internet, Bluetooth, Wi-Fi or storage
permission, so it cannot send your photos anywhere other than the screen.

## Tips for a reliable transfer

- Turn the sender's brightness up (the app does this) and hold both phones still, 20 to 30 cm apart.
- Avoid glare on the sender's screen.
- If the receiver is struggling, slide the sender's speed slider down.
- Fewer photos per transfer means shorter waits; each photo takes about 10 seconds.

## Status

Version 0.1.0. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#known-limits) for what is not done yet.
