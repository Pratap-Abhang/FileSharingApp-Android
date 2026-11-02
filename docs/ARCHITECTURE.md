# Architecture (Android)

## The big picture

```
SENDER                                              RECEIVER
Pick photos -> Compress -> Bundle -> Fountain -> QR loop  ~~light~~>  Camera -> Collect -> Decode -> Verify -> Save
(PickVisualMedia) (WebP)  (Bundle)  (Encoder)   (ZXing)              (CameraX+MLKit) (Decoder) (Decoder) (SHA-256) (MediaStore)
```

The link between the phones is one-way: the receiver cannot ask the sender to repeat a frame. That
single fact explains most of the design.

## Why a fountain code

If the sender showed the photo as numbered QR codes 1..N, the receiver would have to catch every
one, and a single missed frame would mean waiting for the loop to come round again. A fountain code
instead produces an endless stream of packets where **any** set of slightly more than N packets
rebuilds the data. So the receiver can start mid-stream, lose frames to blur or glare, and still
finish. There is no "last frame" and no coordination.

Details (see [../protocol/spec.md](../protocol/spec.md) for the exact bytes):

- The first N packets are the source blocks themselves (a "systematic" start). With a perfect
  camera, the receiver is done after N frames.
- Later packets are the XOR of a few randomly chosen blocks. The choice is a pure function of the
  packet number and the session id, so the frame only has to carry its `esi` (packet number) and
  `degree`, never the list of blocks.
- Degrees follow a robust soliton distribution. The decoder is a "peeling" decoder: a packet with
  one unknown block reveals that block, which simplifies the other stored packets.
- Overhead, measured with the Python reference (extra frames the receiver needed beyond N, losses
  not counted): about 18% for 1000 blocks, 20 to 30% for 200 blocks, and 35 to 130% for tiny bundles of
  25 blocks or fewer. A single 200 KB photo is about 200 blocks, so expect roughly 1.25x the
  frames. Small bundles pay more, but in absolute terms it is only a few dozen frames.

## Modules

### `codec/` (pure Kotlin, no Android classes)

| File | Responsibility |
| --- | --- |
| `Base45.kt` | RFC 9285 Base45; its output only uses QR alphanumeric characters |
| `Frame.kt` | One QR frame: header, payload, CRC32; `parse` rejects anything damaged |
| `Fountain.kt` | xorshift32 PRNG, packet seeds, robust-soliton CDF, block index selection |
| `FountainEncoder.kt` | Bundle bytes to frame number `esi` |
| `FountainDecoder.kt` | Frames to bundle bytes; tracks progress; resets on a new session |
| `Bundle.kt` | Photos + manifest + SHA-256 packed into one byte string, and back |

The codec has no Android dependency so it can be unit-tested on a laptop in seconds and its
behaviour is easy to compare with the Swift version.

### `app/send/`

- `SendViewModel`: state machine (Idle, Preparing, Streaming, Failed) and the frame loop. The loop
  renders the next QR on `Dispatchers.Default` and publishes it to the UI; it sleeps only for the
  time left in the frame's slot so the rate stays steady.
- `ImageCompressor`: `ImageDecoder` decodes with the target size set (cheap, applies EXIF rotation)
  and WebP lossy at quality 70 encodes. Names are sanitised so they are safe on any file system.
- `QrRenderer`: ZXing `Encoder` with error correction L. The bitmap is one pixel per module; the
  UI scales it with `FilterQuality.None` to keep edges crisp.
- `SendScreen`: keeps the screen on at full brightness while codes are showing.

### `app/receive/`

- `QrAnalyzer`: CameraX `ImageAnalysis` at about 1080p feeding ML Kit's bundled QR scanner. It uses
  `KEEP_ONLY_LATEST`, so a slow phone drops frames instead of building a queue.
- `ReceiveViewModel`: funnels every scanned string through a single-threaded dispatcher into the
  (non-thread-safe) `FountainDecoder`. When the decoder finishes it unpacks the bundle (which checks
  the SHA-256) and saves the photos.
- `PhotoSaver`: `MediaStore` insert with `IS_PENDING`, into `Pictures/FileSharingApp`.

## Choices worth knowing about

| Choice | Why |
| --- | --- |
| minSdk 29 | `MediaStore` can save photos with no storage permission, so the app asks only for the camera |
| Base45 in alphanumeric QR mode | QR alphanumeric packs about 5.5 bits per character versus 8 for bytes, but iOS scanners return text, not reliable raw bytes; Base45 works on both |
| Error correction level L | Maximises payload per code. Fountain coding makes up for the weaker error correction by tolerating whole lost frames |
| 1000-byte blocks | A frame is 1024 bytes, 1536 Base45 characters, about QR version 25 (117 modules), still readable by phone cameras |
| 10 frames per second default | Phone cameras run at 30 fps; showing at 10 gives each code about 3 camera frames, so a blurred one does not ruin it |
| No network permission | The app cannot leak data even by mistake |

## Throughput

Estimated, not measured on a device yet: 1000 payload bytes at 10 frames per second is about 10 KB/s
before overhead, so a 200 KB photo takes about 20 to 25 seconds at the default speed. Moving the slider
to 15 frames per second brings that to about 15 seconds when the camera keeps up. The earlier plan
quoted 15 to 30 KB/s from larger frames at higher rates; this first build uses the safer defaults.
Measure on real phones and tune `DEFAULT_FPS` and the block size in `SendViewModel`/`FountainEncoder`.

## Known limits

- Not yet tested on real devices; the UI code was written without an Android SDK available to compile it.
  The first thing to do is open it in Android Studio and fix anything the compiler flags.
- Photos only. Videos and other files would need the bundle format to carry a larger limit and a resume feature.
- One photo batch is held in memory while packing; the picker is capped at 20 photos.
- There is no pause or resume on the receiver; leaving the screen discards partial progress.
- The sender cannot show a QR bigger than the screen allows. Very small phones may need a lower block size.
- The launcher icon is the default one.

## Troubleshooting

| Symptom | Try |
| --- | --- |
| Receiver stuck near 0% | Move closer or farther until the whole code fits in view; remove glare; lower the sender's speed |
| Progress rises then stalls | Normal near the end; the last blocks need specific packets. Keep the camera steady |
| "Photos arrived damaged" | The final SHA-256 check failed; try again. Please report it with the photo count, because it should be very rare |
| Photos missing from the gallery | Look in the **FileSharingApp** album under Pictures |
