# NextLib Extended

[![Publish](https://github.com/Darkstar085/nextlib-extended/actions/workflows/publish.yaml/badge.svg)](https://github.com/Darkstar085/nextlib-extended/actions/workflows/publish.yaml) [![Maven Central](https://img.shields.io/maven-central/v/io.github.darkstar085/nextlib-extended-media3ext.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.darkstar085/nextlib-extended-media3ext)

NextLib Extended is an Android library providing FFmpeg codecs, advanced subtitle rendering (libass), and media extraction extensions for [AndroidX Media3 (ExoPlayer)](https://github.com/androidx/media).

---

## ⚙️ How It Works (Working)

NextLib Extended bridges native C/C++ multimedia libraries to AndroidX Media3 via JNI:
* **Audio & Video Decoding:** Bundles FFmpeg decoders and `dav1d` (AV1). When Android's hardware `MediaCodec` does not support a format (e.g., AC3, EAC3, TrueHD, DTS, AV1), NextLib smoothly falls back to native software rendering.
* **Styled Subtitles:** Uses `libass` along with FreeType and HarfBuzz to accurately render complex ASS/SSA subtitles (karaoke, styling, embedded fonts, animations) as bitmap overlays.
* **Extraction & Probing:** Offers FFmpeg-based container demuxing, metadata extraction, and frame thumbnail retrieval.

### Supported Decoders
* **Audio:** Vorbis, Opus, FLAC, ALAC, PCM (u-law/a-law), MP3, AMR-NB, AMR-WB, AAC, AC3, EAC3, DCA (DTS), MLP, TrueHD
* **Video:** H.264, HEVC (H.265), VP8, VP9, AV1 (`dav1d`)
* **Subtitles:** ASS/SSA (`libass`), PGS, VobSub, DVB

---

## 🛠️ Tech Stack

* **Language:** Kotlin, Java, C/C++ (JNI)
* **Framework:** AndroidX Media3 (ExoPlayer), Android NDK (Clang toolchain)
* **Native Libraries:**
  * FFmpeg 9.x
  * dav1d (AV1 Decoder)
  * mbedTLS (TLS/HTTPS streaming)
  * libass, FreeType, HarfBuzz, FriBidi, Fontconfig (Subtitle rendering)
* **Supported ABIs:** `arm64-v8a`, `armeabi-v7a`

---

## 📁 Project Structure & Modules

```
nextlib-extended/
├── media3ext/          # Media3 extensions: Renderers, Decoders, Subtitles, JNI glue
│   └── src/main/
│       ├── cpp/        # JNI C++ wrapper code (audio, video, subtitles)
│       └── kotlin/     # NextRenderersFactory, subtitle extractors, DecoderManager
├── mediainfo/          # Media metadata extraction & thumbnail generator via FFmpeg
│   └── src/main/
│       ├── cpp/        # Frame extraction & metadata JNI code
│       └── kotlin/     # MediaInfoBuilder, MediaThumbnailRetriever
└── ffmpeg/             # Native cross-compilation scripts (setup.sh) & headers/libs
```

### Modules
1. **`media3ext`**: Provides `NextRenderersFactory`, custom FFmpeg audio/video renderers, ASS subtitle rendering, and dynamic runtime decoder switching.
2. **`mediainfo`**: Provides fast, seekable AVIO metadata parsing and thumbnail generation without reopening files.

---

## 🚀 How to Use (Usage)

### 1. Dependency Setup

Add dependencies to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.darkstar085:nextlib-extended-media3ext:VERSION")
    implementation("io.github.darkstar085:nextlib-extended-mediainfo:VERSION")
}
```

---

### 2. Basic Player Setup

Pass `NextRenderersFactory` to `ExoPlayer` to enable FFmpeg decoders:

```kotlin
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.sipun.nextlib.media3ext.ffdecoder.NextRenderersFactory

// EXTENSION_RENDERER_MODE_ON: Use FFmpeg when platform hardware decoders fail
// EXTENSION_RENDERER_MODE_PREFER: Prioritize FFmpeg decoders over platform decoders
val renderersFactory = NextRenderersFactory(context)
    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

val player = ExoPlayer.Builder(context)
    .setRenderersFactory(renderersFactory)
    .build()
```

---

### 3. Styled Subtitle Support (ASS/SSA)

Enable FFmpeg subtitle extraction and libass rendering:

```kotlin
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sipun.nextlib.media3ext.ffdecoder.FfmpegSubtitleExtractorsFactory
import com.sipun.nextlib.media3ext.ffdecoder.NextRenderersFactory
import com.sipun.nextlib.media3ext.ffdecoder.withFfmpegSubtitles

val dataSourceFactory = DefaultDataSource.Factory(context)
val mediaSourceFactory = DefaultMediaSourceFactory(context, FfmpegSubtitleExtractorsFactory())
    .setDataSourceFactory(dataSourceFactory)
    .withFfmpegSubtitles(dataSourceFactory)

val player = ExoPlayer.Builder(context)
    .setRenderersFactory(NextRenderersFactory(context))
    .setMediaSourceFactory(mediaSourceFactory)
    .build()
```

---

### 4. Metadata & Thumbnails Extraction

Extract track details, resolution, bitrate, or thumbnails directly using FFmpeg:

```kotlin
import com.sipun.nextlib.mediainfo.MediaInfoBuilder
import com.sipun.nextlib.mediainfo.MediaThumbnailRetriever

// Extract media information
val mediaInfo = MediaInfoBuilder.from(fileDescriptor).build()
val duration = mediaInfo.duration
val videoTracks = mediaInfo.videoStreams

// Retrieve video thumbnail at given timestamp (microseconds)
val retriever = MediaThumbnailRetriever()
retriever.setDataSource(fileDescriptor)
val bitmap = retriever.getFrameAtTime(5_000_000) // 5 seconds
retriever.release()
```

---

### 5. Runtime Decoder Switching (Hardware vs Software)

Dynamically switch between Hardware and FFmpeg decoders without recreating the player:

```kotlin
import com.sipun.nextlib.media3ext.ffdecoder.DecoderManager
import com.sipun.nextlib.media3ext.ffdecoder.DecoderMode
import com.sipun.nextlib.media3ext.ffdecoder.NextRenderersFactory

val decoderManager = DecoderManager()
val renderersFactory = NextRenderersFactory(context)
    .setDecoderManager(decoderManager)

val player = ExoPlayer.Builder(context)
    .setRenderersFactory(renderersFactory)
    .build()

decoderManager.attach(player)

// Switch decoders on the fly:
decoderManager.selectVideoDecoder(DecoderMode.HARDWARE) // Or DecoderMode.SOFTWARE
decoderManager.selectAudioDecoder(DecoderMode.AUTO)

// Cleanup on destroy:
decoderManager.detach()
player.release()
```

---

## 📄 License

NextLib Extended is licensed under the [GNU General Public License v3.0](LICENSE).
