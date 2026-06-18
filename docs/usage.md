# TranscodeX SDK - Usage Documentation

This guide covers everything you need to install, configure, integrate, and tune the TranscodeX SDK in plain Java, Spring Boot, batch systems, and command-line environments.

---

## 1. Installation Guide

Add the TranscodeX dependencies to your `pom.xml`. Since this is a modular SDK, you import the API layer and the FFmpeg implementation engine:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.transcodex</groupId>
            <artifactId>transcodex-sdk</artifactId>
            <version>0.1.0-alpha1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Core API models & Interfaces -->
    <dependency>
        <groupId>io.transcodex</groupId>
        <artifactId>transcodex-api</artifactId>
    </dependency>
    
    <!-- Core immutable Records -->
    <dependency>
        <groupId>io.transcodex</groupId>
        <artifactId>transcodex-core</artifactId>
    </dependency>

    <!-- FFmpeg Concrete Implementations -->
    <dependency>
        <groupId>io.transcodex</groupId>
        <artifactId>transcodex-ffmpeg</artifactId>
    </dependency>
</dependencies>
```

### System Requirements
Before running your application, ensure `ffmpeg` and `ffprobe` are installed on the host machine and registered in the system `PATH`:
- **Windows**: Add the folders containing `ffmpeg.exe` and `ffprobe.exe` to System Environment Variables under `Path`.
- **Linux/macOS**: Verify installation via `which ffmpeg` and `which ffprobe`.

---

## 2. Plain Java Application Guide

Below is a complete standalone application displaying metadata extraction, thumbnail frame capturing, and multi-variant transcoding:

```java
package io.transcodex.example;

import io.transcodex.api.video.DefaultVideoProcessor;
import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import io.transcodex.ffmpeg.video.FfmpegThumbnailGenerator;
import io.transcodex.ffmpeg.video.FfmpegVideoTranscoder;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StandaloneApp {

    public static void main(String[] args) {
        Path inputVideo = Paths.get("videos/input.mp4");
        Path outputDirectory = Paths.get("output/");

        // Setup process runners
        ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
        FfprobeMetadataExtractor metadataExtractor = new FfprobeMetadataExtractor(executor, new JacksonMetadataParser());
        FfmpegThumbnailGenerator thumbnailGenerator = new FfmpegThumbnailGenerator(executor);
        FfmpegVideoTranscoder videoTranscoder = new FfmpegVideoTranscoder(executor);

        // Configure Java Virtual Threads for non-blocking I/O orchestration
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            DefaultVideoProcessor processor = new DefaultVideoProcessor(
                metadataExtractor,
                videoTranscoder,
                thumbnailGenerator,
                Optional.empty(), // No cloud storage configured
                virtualExecutor
            );

            // Configure pipeline request
            VideoRequest request = VideoRequest.builder()
                .source(inputVideo)
                .outputDir(outputDirectory)
                .resolution(VideoResolution.P360)
                .resolution(VideoResolution.P720)
                .generateThumbnail(true)
                .thumbnailOptions(new ThumbnailOptions(320, 180, 5.0, "jpg"))
                .build();

            System.out.println("Processing video pipeline...");
            VideoResult result = processor.process(request);
            
            // Output results
            System.out.println("Metadata Format: " + result.metadata().format());
            System.out.println("Metadata Duration: " + result.metadata().durationSeconds() + "s");
            result.thumbnail().ifPresent(t -> System.out.println("Thumbnail extracted: " + t.path()));
            result.transcodedAssets().forEach(asset -> 
                System.out.println("Transcoded variant: " + asset.resolution().label() + " -> " + asset.path())
            );

        } catch (Exception e) {
            System.err.println("Pipeline failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

---

## 3. Spring Boot Integration Guide

Integrate the SDK into a Spring Boot application using virtual threads and clean bean wiring.

### Configuration Properties
Define paths in your `application.yml`:
```yaml
transcodex:
  ffmpeg-path: /usr/bin/ffmpeg
  ffprobe-path: /usr/bin/ffprobe
```

### Dependency Configuration Class
```java
package io.transcodex.springboot.config;

import io.transcodex.api.video.VideoProcessor;
import io.transcodex.api.video.DefaultVideoProcessor;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import io.transcodex.ffmpeg.video.FfmpegThumbnailGenerator;
import io.transcodex.ffmpeg.video.FfmpegVideoTranscoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class TranscodeXConfiguration {

    @Value("${transcodex.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${transcodex.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Bean
    public ExecutorService transcodeExecutorService() {
        // Enforce virtual thread-per-task executor
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public ProcessBuilderExecutor processBuilderExecutor() {
        return new ProcessBuilderExecutor();
    }

    @Bean
    public VideoProcessor videoProcessor(ProcessBuilderExecutor executor, ExecutorService executorService) {
        FfprobeMetadataExtractor metadataExtractor = new FfprobeMetadataExtractor(executor, new JacksonMetadataParser(), ffprobePath);
        FfmpegThumbnailGenerator thumbnailGenerator = new FfmpegThumbnailGenerator(executor, ffmpegPath);
        FfmpegVideoTranscoder videoTranscoder = new FfmpegVideoTranscoder(executor, ffmpegPath);

        return new DefaultVideoProcessor(
            metadataExtractor,
            videoTranscoder,
            thumbnailGenerator,
            Optional.empty(),
            executorService
        );
    }
}
```

### VideoProcessingService Implementation
```java
package io.transcodex.springboot.service;

import io.transcodex.api.video.VideoProcessor;
import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class VideoProcessingService {

    private final VideoProcessor videoProcessor;

    public VideoProcessingService(VideoProcessor videoProcessor) {
        this.videoProcessor = videoProcessor;
    }

    public VideoResult processUploadedVideo(Path fileLocation, Path targetDirectory) {
        VideoRequest request = VideoRequest.builder()
            .source(fileLocation)
            .outputDir(targetDirectory)
            .resolution(VideoResolution.P720)
            .resolution(VideoResolution.P1080)
            .generateThumbnail(true)
            .thumbnailOptions(new ThumbnailOptions(640, 360, 2.0, "png"))
            .build();

        return videoProcessor.process(request);
    }
}
```

---

## 4. Batch Processing Application

This recipe illustrates how to batch process multiple videos concurrently, capturing errors for individual files without failing the entire batch:

```java
package io.transcodex.example;

import io.transcodex.api.video.VideoProcessor;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class BatchProcessor {

    private final VideoProcessor processor;
    private final ExecutorService executorService;

    public BatchProcessor(VideoProcessor processor, ExecutorService executorService) {
        this.processor = processor;
        this.executorService = executorService;
    }

    public void runBatch(List<Path> inputVideos, Path outputDirectory) {
        List<CompletableFuture<Void>> jobs = inputVideos.stream()
            .map(video -> CompletableFuture.runAsync(() -> {
                try {
                    VideoRequest request = VideoRequest.builder()
                        .source(video)
                        .outputDir(outputDirectory)
                        .resolution(VideoResolution.P480)
                        .build();

                    VideoResult result = processor.process(request);
                    System.out.printf("Successfully processed: %s. Output tracks: %d\n", 
                        video.getFileName(), result.transcodedAssets().size());
                } catch (Exception e) {
                    System.err.printf("Error processing %s: %s\n", video.getFileName(), e.getMessage());
                }
            }, executorService))
            .toList();

        // Wait for all batch tasks to finish
        CompletableFuture.allOf(jobs.toArray(new CompletableFuture[0])).join();
        System.out.println("Batch processing completed.");
    }
}
```

---

## 5. Performance Tuning Guide

### Task Scheduling & Concurrency
- Always inject a virtual-thread executor (`Executors.newVirtualThreadPerTaskExecutor()`). Virtual threads run non-blocking OS shell tasks at practically zero thread-switching cost.
- Avoid using `ForkJoinPool.commonPool()` in production, as thread pooling conflicts with other framework tasks.

### Preset Speed Configurations
By default, the SDK compiles transcodes using the `-preset fast` command. For faster processing (with slightly larger files), you can subclass or modify transcoder options to use `-preset ultrafast` or `-preset superfast` to trade file size for speed.

---

## 6. Troubleshooting Guide

### Issue: `TranscodingException: ffmpeg transcode failed with exit code 1`
* **Root Cause**: Invalid media formats or incorrect options sent to the FFmpeg binary.
* **Resolution**: Examine the exception's nested message. The SDK grabs and exposes the raw standard error output (`stderr`) from the native execution. Common problems include trying to scale a portrait video to standard landscape ratios without preserving padding.

### Issue: Process hangs indefinitely
* **Root Cause**: In custom executors, failing to drain standard input/error streams will block the OS buffer, pausing the process indefinitely.
* **Resolution**: Use `ProcessBuilderExecutor` provided by this SDK. It uses background Virtual Threads to drain buffers, preventing blocking.

### Issue: `Cannot run program "ffmpeg": CreateProcess error=2, The system cannot find the file specified`
* **Root Cause**: The binary `ffmpeg` cannot be found in the current OS environment `PATH`.
* **Resolution**: Verify installation by running `ffmpeg -version` in a command shell, or supply the absolute path directly to the `FfmpegVideoTranscoder` constructor.
