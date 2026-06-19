package io.transcodex.api.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.api.storage.StorageProvider;
import io.transcodex.core.metadata.AudioStreamMetadata;
import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.metadata.VideoStreamMetadata;
import io.transcodex.core.video.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultVideoProcessorTest {

  private MetadataExtractor metadataExtractor;
  private VideoTranscoder videoTranscoder;
  private ThumbnailGenerator thumbnailGenerator;
  private StorageProvider storageProvider;

  private DefaultVideoProcessor processor;

  @BeforeEach
  void setUp() {
    metadataExtractor = mock(MetadataExtractor.class);
    videoTranscoder = mock(VideoTranscoder.class);
    thumbnailGenerator = mock(ThumbnailGenerator.class);
    storageProvider = mock(StorageProvider.class);

    processor =
        new DefaultVideoProcessor(
            metadataExtractor,
            videoTranscoder,
            thumbnailGenerator,
            Optional.of(storageProvider),
            java.util.concurrent.Executors.newFixedThreadPool(2));
  }

  @Test
  void shouldSuccessfullyProcessPipelineWithoutStorage(@TempDir Path tempDir) throws IOException {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path outputDir = tempDir.resolve("output");

    VideoMetadata metadata =
        new VideoMetadata(
            Duration.ofSeconds(10),
            1000L,
            100L,
            "mp4",
            new VideoStreamMetadata("h264", 1280, 720, 30.0, "16:9", 80L),
            Optional.of(new AudioStreamMetadata("aac", 2, 48000, 20L)));

    when(metadataExtractor.extract(source)).thenReturn(metadata);

    VideoRequest request =
        VideoRequest.builder()
            .source(source)
            .outputDir(outputDir)
            .resolution(VideoResolution.P720)
            .generateThumbnail(true)
            .build();

    // Instantiate processor without storage
    DefaultVideoProcessor noStorageProcessor =
        new DefaultVideoProcessor(metadataExtractor, videoTranscoder, thumbnailGenerator);

    VideoResult result = noStorageProcessor.process(request);

    assertThat(result.metadata()).isEqualTo(metadata);
    assertThat(result.thumbnail()).isPresent();
    assertThat(result.transcodedAssets()).hasSize(1);
    assertThat(result.transcodedAssets().get(0).resolution()).isEqualTo(VideoResolution.P720);
    assertThat(result.storedAssetKeys()).isEmpty();

    verify(metadataExtractor).extract(source);
    verify(videoTranscoder).transcode(eq(source), any(Path.class), any(TranscodingOptions.class));
    verify(thumbnailGenerator).generate(eq(source), any(Path.class), any(ThumbnailOptions.class));
    verifyNoInteractions(storageProvider);
  }

  @Test
  void shouldSuccessfullyProcessPipelineWithStorage(@TempDir Path tempDir) throws IOException {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path outputDir = tempDir.resolve("output");

    VideoMetadata metadata =
        new VideoMetadata(
            Duration.ofSeconds(10),
            1000L,
            100L,
            "mp4",
            new VideoStreamMetadata("h264", 1280, 720, 30.0, "16:9", 80L),
            Optional.empty());

    when(metadataExtractor.extract(source)).thenReturn(metadata);

    VideoRequest request =
        VideoRequest.builder()
            .source(source)
            .outputDir(outputDir)
            .resolution(VideoResolution.P720)
            .generateThumbnail(true)
            .storagePrefix("assets")
            .build();

    VideoResult result = processor.process(request);

    assertThat(result.metadata()).isEqualTo(metadata);
    assertThat(result.storedAssetKeys()).hasSize(2); // thumbnail + 720p video
    assertThat(result.storedAssetKeys().get(0)).startsWith("assets/");

    // Verify storage upload was invoked
    verify(storageProvider, times(2)).store(any(Path.class), any(String.class));

    // Verify local files cleanup worked
    if (result.thumbnail().isPresent()) {
      assertThat(Files.exists(result.thumbnail().get().path())).isFalse();
    }
    for (VideoAsset asset : result.transcodedAssets()) {
      assertThat(Files.exists(asset.path())).isFalse();
    }
  }

  @Test
  void shouldPropagateExceptionWhenTranscoderFails(@TempDir Path tempDir) throws IOException {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path outputDir = tempDir.resolve("output");

    VideoMetadata metadata =
        new VideoMetadata(
            Duration.ofSeconds(10),
            1000L,
            100L,
            "mp4",
            new VideoStreamMetadata("h264", 1280, 720, 30.0, "16:9", 80L),
            Optional.empty());

    when(metadataExtractor.extract(source)).thenReturn(metadata);
    doThrow(new TranscodingException("Transcode error"))
        .when(videoTranscoder)
        .transcode(eq(source), any(Path.class), any(TranscodingOptions.class));

    VideoRequest request =
        VideoRequest.builder()
            .source(source)
            .outputDir(outputDir)
            .resolution(VideoResolution.P720)
            .build();

    assertThatThrownBy(() -> processor.process(request))
        .isInstanceOf(TranscodingException.class)
        .hasMessage("Transcode error");
  }

  @Test
  void shouldPropagateExceptionWhenMetadataExtractorFails(@TempDir Path tempDir)
      throws IOException {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path outputDir = tempDir.resolve("output");

    when(metadataExtractor.extract(source))
        .thenThrow(new MetadataExtractionException("Metadata error"));

    VideoRequest request =
        VideoRequest.builder()
            .source(source)
            .outputDir(outputDir)
            .resolution(VideoResolution.P720)
            .build();

    assertThatThrownBy(() -> processor.process(request))
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessage("Metadata error");

    verifyNoInteractions(videoTranscoder, thumbnailGenerator, storageProvider);
  }
}
