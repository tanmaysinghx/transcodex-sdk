# TranscodeX SDK v0.1.0-alpha1

First public alpha release of TranscodeX SDK.

## Features

### Core Domain Layer

* Immutable Java 25 records
* Video metadata model
* Stream metadata model
* Thumbnail and asset abstractions
* Processing lifecycle states

### API Contracts

* VideoProcessor contract
* MetadataExtractor contract
* StorageProvider contract

### Storage Abstractions

* StorageObject model
* Provider-based storage architecture

### FFmpeg Foundation

* Process execution abstraction
* Virtual-thread enabled ProcessBuilder executor
* CommandResult model
* Metadata parsing infrastructure

### Metadata Extraction

* FFprobe integration
* Jackson-based metadata parsing
* Video and audio stream inspection

## Status

Alpha Release

The SDK currently focuses on metadata extraction and foundational media-processing infrastructure.

Thumbnail generation, transcoding, adaptive streaming, encryption, and storage implementations are under active development.

## Compatibility

* Java 25
* Maven Multi-Module Architecture
* FFmpeg / FFprobe Required
