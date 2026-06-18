# Changelog

All notable changes to the TranscodeX SDK project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.1.0-alpha1] - 2026-06-18

This is the first public alpha release of TranscodeX SDK.

### Added
- **Multi-Module Layout**: Modular Maven build structure separating interfaces (`transcodex-api`), core models (`transcodex-core`), FFmpeg concrete engines (`transcodex-ffmpeg`), storage utilities (`transcodex-storage`), and performance harnesses (`transcodex-benchmarks`).
- **Core Abstractions**: Lightweight record models (`VideoMetadata`, `StreamMetadata`, `ThumbnailOptions`, `TranscodingOptions`) using Java 25.
- **Process Orchestration**: Built `ProcessBuilderExecutor` implementing `FfmpegExecutor` to safely drain stdout/stderr buffers concurrently on Java Virtual Threads.
- **Metadata Parsing**: Jackson-based JSON structure mapping parser (`JacksonMetadataParser`) decoupling external shell executions from mapping rules.
- **Thumbnail Capture**: Introduced `FfmpegThumbnailGenerator` to capture and scale single frames.
- **Video Transcoder**: Implemented `FfmpegVideoTranscoder` for multi-variant scaling and codec parameters wrapping.
- **Benchmarking Suite**: Set up a shaded runnable JMH suite (`transcodex-benchmarks`) measuring scheduling latency and file conversions.

### Changed
- **Parser Decoupling**: Refactored `FfprobeMetadataExtractor` to delegate JSON stream mapping tasks to the isolated `MetadataParser` implementation.
