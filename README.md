# TranscodeX SDK

TranscodeX is a Java 25 SDK for running common FFmpeg workflows from Java:

- read video and audio metadata with FFprobe
- generate thumbnails
- transcode one or more resolutions
- create HLS playlists with optional AES-128 encryption
- upload generated assets through a storage provider

## Requirements

- JDK 25
- Maven 3.9+
- `ffmpeg` and `ffprobe` available on `PATH`

## Build

```bash
mvn clean verify
```

Integration tests invoke FFmpeg, so both binaries must be installed before running the full suite.

## Dependency

Install the current snapshot locally:

```bash
mvn clean install
```

Then add the aggregate dependency:

```xml
<dependency>
    <groupId>io.transcodex</groupId>
    <artifactId>transcodex-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Applications that need tighter dependency control can use `transcodex-api`,
`transcodex-core`, and `transcodex-ffmpeg` individually.

## Example

```java
var executor = new ProcessBuilderExecutor();
var metadata = new FfprobeMetadataExtractor(executor, new JacksonMetadataParser());
var thumbnails = new FfmpegThumbnailGenerator(executor);
var transcoder = new FfmpegVideoTranscoder(executor);
var processor = new DefaultVideoProcessor(metadata, transcoder, thumbnails);

var request = VideoRequest.builder()
    .source(Path.of("input.mp4"))
    .outputDir(Path.of("output"))
    .resolution(VideoResolution.P720)
    .generateThumbnail(true)
    .build();

VideoResult result = processor.process(request);
```

`DefaultVideoProcessor` also accepts a `StorageProvider` and a custom `ExecutorService`.
See the tests for HLS, encryption, storage, and custom option examples.

## Modules

| Module | Purpose |
| --- | --- |
| `transcodex-core` | Immutable request, result, metadata, and option types |
| `transcodex-api` | Public processing and storage contracts |
| `transcodex-ffmpeg` | FFmpeg and FFprobe implementations |
| `transcodex-storage` | Shared storage object model |
| `transcodex-starter` | Aggregate dependency for consumers |
| `transcodex-playground` | Standalone Spring Boot demo |

## License

Apache License 2.0. See [LICENSE](LICENSE).
