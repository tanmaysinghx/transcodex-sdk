package io.transcodex.ffmpeg.video;

import static org.assertj.core.api.Assertions.assertThat;

import io.transcodex.api.video.DefaultVideoProcessor;
import io.transcodex.core.video.*;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VideoProcessorIntegrationTest {

  private static Path tempDir;
  private static Path sourceVideo;

  @BeforeAll
  static void beforeAll() throws Exception {
    tempDir = Files.createTempDirectory("transcodex-processor-integration");
    sourceVideo = tempDir.resolve("source_input.mp4");

    // Generate a real 1-second sample video using ffmpeg
    new ProcessBuilderExecutor()
        .execute(
            List.of(
                "ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "color=c=blue:s=1280x720:d=1",
                "-c:v",
                "libx264",
                "-pix_fmt",
                "yuv420p",
                sourceVideo.toAbsolutePath().toString()));
  }

  @AfterAll
  static void afterAll() throws IOException {
    if (tempDir != null) {
      try (var walk = Files.walk(tempDir)) {
        walk.sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(java.io.File::delete);
      }
    }
  }

  @Test
  void shouldSuccessfullyProcessEndToEndPipeline(@TempDir Path outputDir) {
    // 1. Arrange
    ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
    FfprobeMetadataExtractor metadataExtractor =
        new FfprobeMetadataExtractor(executor, new JacksonMetadataParser());
    FfmpegThumbnailGenerator thumbnailGenerator = new FfmpegThumbnailGenerator(executor);
    FfmpegVideoTranscoder videoTranscoder = new FfmpegVideoTranscoder(executor);

    DefaultVideoProcessor processor =
        new DefaultVideoProcessor(
            metadataExtractor,
            videoTranscoder,
            thumbnailGenerator,
            Optional.empty(),
            Executors.newVirtualThreadPerTaskExecutor());

    ThumbnailOptions thumbOpts = new ThumbnailOptions(320, 180, 0.5, "jpg");

    VideoRequest request =
        VideoRequest.builder()
            .source(sourceVideo)
            .outputDir(outputDir)
            .resolution(VideoResolution.P360)
            .resolution(VideoResolution.P720)
            .generateThumbnail(true)
            .thumbnailOptions(thumbOpts)
            .build();

    // 2. Act
    VideoResult result = processor.process(request);

    // 3. Assert
    assertThat(result.metadata()).isNotNull();
    assertThat(result.metadata().format()).contains("mp4");
    assertThat(result.metadata().videoStream().width()).isEqualTo(1280);
    assertThat(result.metadata().videoStream().height()).isEqualTo(720);

    // Thumbnail assertion
    assertThat(result.thumbnail()).isPresent();
    Thumbnail thumbnail = result.thumbnail().get();
    assertThat(Files.exists(thumbnail.path())).isTrue();
    assertThat(thumbnail.width()).isEqualTo(320);
    assertThat(thumbnail.height()).isEqualTo(180);
    assertThat(thumbnail.format()).isEqualTo("jpg");

    // Transcoded variants assertion
    assertThat(result.transcodedAssets()).hasSize(2);

    VideoAsset p360Asset =
        result.transcodedAssets().stream()
            .filter(a -> a.resolution() == VideoResolution.P360)
            .findFirst()
            .orElseThrow();
    assertThat(Files.exists(p360Asset.path())).isTrue();
    assertThat(p360Asset.sizeBytes()).isGreaterThan(0L);

    VideoAsset p720Asset =
        result.transcodedAssets().stream()
            .filter(a -> a.resolution() == VideoResolution.P720)
            .findFirst()
            .orElseThrow();
    assertThat(Files.exists(p720Asset.path())).isTrue();
    assertThat(p720Asset.sizeBytes()).isGreaterThan(0L);

    // Master playlist should not exist for non-HLS
    assertThat(result.masterPlaylist()).isEmpty();
    assertThat(result.encryptionKeyFile()).isEmpty();
  }

  @Test
  void shouldSuccessfullyProcessHlsPipeline(@TempDir Path outputDir) {
    // 1. Arrange
    ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
    FfprobeMetadataExtractor metadataExtractor =
        new FfprobeMetadataExtractor(executor, new JacksonMetadataParser());
    FfmpegThumbnailGenerator thumbnailGenerator = new FfmpegThumbnailGenerator(executor);
    FfmpegVideoTranscoder videoTranscoder = new FfmpegVideoTranscoder(executor);

    DefaultVideoProcessor processor =
        new DefaultVideoProcessor(
            metadataExtractor,
            videoTranscoder,
            thumbnailGenerator,
            Optional.empty(),
            Executors.newVirtualThreadPerTaskExecutor());

    VideoRequest request =
        VideoRequest.builder()
            .source(sourceVideo)
            .outputDir(outputDir)
            .resolution(VideoResolution.P360)
            .generateHls(true)
            .build();

    // 2. Act
    VideoResult result = processor.process(request);

    // 3. Assert
    assertThat(result.transcodedAssets()).hasSize(1);
    VideoAsset hlsAsset = result.transcodedAssets().get(0);
    assertThat(hlsAsset.resolution()).isEqualTo(VideoResolution.P360);
    assertThat(hlsAsset.path().getFileName().toString()).endsWith(".m3u8");
    assertThat(Files.exists(hlsAsset.path())).isTrue();

    // Verify master playlist
    assertThat(result.masterPlaylist()).isPresent();
    assertThat(Files.exists(result.masterPlaylist().get())).isTrue();
    assertThat(result.masterPlaylist().get().getFileName().toString()).isEqualTo("master.m3u8");

    // No encryption for this test
    assertThat(result.encryptionKeyFile()).isEmpty();

    // Verify segments exist (.ts files)
    String baseName = hlsAsset.path().getFileName().toString().replace(".m3u8", "");
    try (var stream = Files.list(outputDir)) {
      long tsCount =
          stream
              .filter(
                  p ->
                      p.getFileName().toString().endsWith(".ts")
                          && p.getFileName().toString().startsWith(baseName))
              .count();
      assertThat(tsCount).isGreaterThan(0L);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void shouldSuccessfullyProcessEncryptedHlsPipeline(@TempDir Path outputDir) {
    // 1. Arrange
    ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
    FfprobeMetadataExtractor metadataExtractor =
        new FfprobeMetadataExtractor(executor, new JacksonMetadataParser());
    FfmpegThumbnailGenerator thumbnailGenerator = new FfmpegThumbnailGenerator(executor);
    FfmpegVideoTranscoder videoTranscoder = new FfmpegVideoTranscoder(executor);

    DefaultVideoProcessor processor =
        new DefaultVideoProcessor(
            metadataExtractor,
            videoTranscoder,
            thumbnailGenerator,
            Optional.empty(),
            Executors.newVirtualThreadPerTaskExecutor());

    VideoRequest request =
        VideoRequest.builder()
            .source(sourceVideo)
            .outputDir(outputDir)
            .resolution(VideoResolution.P360)
            .resolution(VideoResolution.P720)
            .encryptChunks(true)
            .encodingThreads(2)
            .build();

    // 2. Act
    VideoResult result = processor.process(request);

    // 3. Assert — encryption key exists
    assertThat(result.encryptionKeyFile()).isPresent();
    Path keyFile = result.encryptionKeyFile().get();
    assertThat(Files.exists(keyFile)).isTrue();
    try {
      assertThat(Files.size(keyFile)).isEqualTo(16L); // AES-128 = 16 bytes
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Key info file exists
    Path keyInfoFile = outputDir.resolve("key_info.txt");
    assertThat(Files.exists(keyInfoFile)).isTrue();

    // Master playlist exists and references both variants
    assertThat(result.masterPlaylist()).isPresent();
    Path masterPlaylist = result.masterPlaylist().get();
    assertThat(Files.exists(masterPlaylist)).isTrue();
    try {
      String masterContent = Files.readString(masterPlaylist);
      assertThat(masterContent).contains("#EXTM3U");
      assertThat(masterContent).contains("#EXT-X-STREAM-INF");
      assertThat(masterContent).contains("RESOLUTION=640x360");
      assertThat(masterContent).contains("RESOLUTION=1280x720");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // Transcoded HLS assets exist
    assertThat(result.transcodedAssets()).hasSize(2);
    for (VideoAsset asset : result.transcodedAssets()) {
      assertThat(asset.path().getFileName().toString()).endsWith(".m3u8");
      assertThat(Files.exists(asset.path())).isTrue();

      // Verify the variant playlist references the encryption key
      try {
        String playlistContent = Files.readString(asset.path());
        assertThat(playlistContent).contains("#EXT-X-KEY:METHOD=AES-128");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    // Verify encrypted .ts segments exist
    try (var stream = Files.list(outputDir)) {
      long tsCount = stream.filter(p -> p.getFileName().toString().endsWith(".ts")).count();
      assertThat(tsCount).isGreaterThan(0L);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
