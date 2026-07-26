# TubeForge

A lightweight Java desktop video editor focused on fast YouTube publishing.

## Features

- Import multiple video clips
- Record your screen or webcam, with optional microphone audio and adjustable voice volume
- Drag-free timeline with reorder and remove controls
- Per-clip in/out trimming
- Generated thumbnail preview
- Project title and channel name overlay
- 1080p, 1440p, 4K, and YouTube Shorts export presets
- H.264/AAC MP4 export through FFmpeg
- Project save/load (`.tforge`)

## Requirements

- Java 17 or newer
- FFmpeg and FFprobe available on `PATH`

## Run

```bash
./run.sh
```

Build a portable runnable JAR:

```bash
./build.sh
java -jar build/TubeForge.jar
```

## Quick workflow

1. Select **Import clips**.
2. Choose a clip in the timeline and set its in/out times.
3. Reorder clips with the arrow buttons.
4. Add an optional title and channel label.
5. Choose a YouTube preset and select **Export video**.

Select **Record** to capture your screen or webcam. Choose whether to include the
microphone, then set **Microphone volume** from 0% (mute) to 200% (boost).
Choose where to save the MP4. When you stop, the recording is imported into the
timeline automatically.

On Linux, screen capture uses X11 and webcam capture uses `/dev/video0`.
Microphone capture uses the default PulseAudio/PipeWire input. On macOS, grant
Terminal/Java screen and camera permissions when prompted. Device numbering can
vary on systems with multiple cameras.

TubeForge creates temporary normalized clips during export, joins them, then produces
an upload-ready MP4. Source files are never modified.
