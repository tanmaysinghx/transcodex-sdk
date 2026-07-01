package io.transcodex.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.transcodex.core.config.TranscodexProperties;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranscodeXCliTest {

  private TranscodexProperties properties;
  private Path tempInputFile;
  private Path tempOutputDir;

  @BeforeEach
  void setUp(@TempDir Path tempDir) throws IOException {
    properties = new TranscodexProperties();
    tempInputFile = tempDir.resolve("sample.mp4");
    Files.createFile(tempInputFile);
    tempOutputDir = tempDir.resolve("output");
  }

  @Test
  void shouldReturnNullWhenHelpRequested() {
    String[] args = {"--help"};
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);
    assertThat(request).isNull();

    String[] argsShort = {"-h"};
    VideoRequest requestShort = TranscodeXCli.parseArgs(argsShort, properties);
    assertThat(requestShort).isNull();
  }

  @Test
  void shouldThrowExceptionWhenInputMissing() {
    String[] args = {"-o", tempOutputDir.toString()};
    assertThatThrownBy(() -> TranscodeXCli.parseArgs(args, properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing required argument -i/--input");
  }

  @Test
  void shouldThrowExceptionWhenOutputMissing() {
    String[] args = {"-i", tempInputFile.toString()};
    assertThatThrownBy(() -> TranscodeXCli.parseArgs(args, properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing required argument -o/--output");
  }

  @Test
  void shouldThrowExceptionWhenInputDoesNotExist(@TempDir Path tempDir) {
    Path nonExistentInput = tempDir.resolve("ghost.mp4");
    String[] args = {"-i", nonExistentInput.toString(), "-o", tempOutputDir.toString()};
    assertThatThrownBy(() -> TranscodeXCli.parseArgs(args, properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Input file does not exist");
  }

  @Test
  void shouldParseBasicArgsWithDefaultProperties() {
    String[] args = {"-i", tempInputFile.toString(), "-o", tempOutputDir.toString()};
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);

    assertThat(request).isNotNull();
    assertThat(request.source()).isEqualTo(tempInputFile);
    assertThat(request.outputDir()).isEqualTo(tempOutputDir);
    // Defaults from properties
    assertThat(request.resolutions())
        .containsExactlyInAnyOrder(VideoResolution.P360, VideoResolution.P720);
    assertThat(request.generateThumbnail()).isTrue();
    assertThat(request.generateHls()).isTrue();
    assertThat(request.encryptChunks()).isFalse();
    assertThat(request.encodingThreads()).isEqualTo(4);
  }

  @Test
  void shouldParseCustomResolutions() {
    String[] args = {
      "-i", tempInputFile.toString(),
      "-o", tempOutputDir.toString(),
      "-r", "1080p,4k,unknown_res"
    };
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);

    assertThat(request).isNotNull();
    assertThat(request.resolutions())
        .containsExactlyInAnyOrder(VideoResolution.P1080, VideoResolution.P2160);
  }

  @Test
  void shouldParseEncryptionAndForceHls() {
    String[] args = {
      "-i", tempInputFile.toString(),
      "-o", tempOutputDir.toString(),
      "-e"
    };
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);

    assertThat(request).isNotNull();
    assertThat(request.encryptChunks()).isTrue();
    assertThat(request.generateHls()).isTrue();
  }

  @Test
  void shouldOverrideThreads() {
    String[] args = {
      "-i", tempInputFile.toString(),
      "-o", tempOutputDir.toString(),
      "-t", "8"
    };
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);

    assertThat(request).isNotNull();
    assertThat(request.encodingThreads()).isEqualTo(8);
  }

  @Test
  void shouldDisableHlsAndThumbnails() {
    String[] args = {
      "-i",
      tempInputFile.toString(),
      "-o",
      tempOutputDir.toString(),
      "--no-hls",
      "--no-thumbnail",
      "-r",
      "720p" // Need at least one resolution if thumbnail is disabled
    };
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);

    assertThat(request).isNotNull();
    assertThat(request.generateHls()).isFalse();
    assertThat(request.generateThumbnail()).isFalse();
  }

  @Test
  void shouldOverrideThumbnailOptions() {
    String[] args = {
      "-i", tempInputFile.toString(),
      "-o", tempOutputDir.toString(),
      "--thumbnail-width", "640",
      "--thumbnail-height", "360",
      "--thumbnail-pos", "5.5",
      "--thumbnail-format", "png"
    };
    VideoRequest request = TranscodeXCli.parseArgs(args, properties);

    assertThat(request).isNotNull();
    assertThat(request.generateThumbnail()).isTrue();
    assertThat(request.thumbnailOptions()).isPresent();
    assertThat(request.thumbnailOptions().get().width()).isEqualTo(640);
    assertThat(request.thumbnailOptions().get().height()).isEqualTo(360);
    assertThat(request.thumbnailOptions().get().positionSeconds()).isEqualTo(5.5);
    assertThat(request.thumbnailOptions().get().format()).isEqualTo("png");
  }
}
