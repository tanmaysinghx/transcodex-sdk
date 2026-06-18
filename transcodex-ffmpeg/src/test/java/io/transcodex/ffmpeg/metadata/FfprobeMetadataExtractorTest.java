package io.transcodex.ffmpeg.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfprobeMetadataExtractorTest {

  private FfmpegExecutor executor;
  private FfprobeMetadataExtractor extractor;

  @TempDir Path tempDir;
  private Path testFile;

  @BeforeEach
  void setUp() throws Exception {
    executor = mock(FfmpegExecutor.class);
    extractor = new FfprobeMetadataExtractor(executor);
    testFile = Files.createFile(tempDir.resolve("test.mp4"));
  }

  @Test
  void shouldSuccessfullyExtractVideoAndAudioMetadata() throws Exception {
    String jsonOutput =
        """
        {
          "format": {
            "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
            "duration": "10.500000",
            "size": "500000",
            "bit_rate": "400000"
          },
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "h264",
              "width": 1920,
              "height": 1080,
              "r_frame_rate": "30/1",
              "display_aspect_ratio": "16:9",
              "bit_rate": "350000"
            },
            {
              "codec_type": "audio",
              "codec_name": "aac",
              "channels": 2,
              "sample_rate": 48000,
              "bit_rate": "50000"
            }
          ]
        }
        """;

    when(executor.execute(anyList())).thenReturn(new CommandResult(0, jsonOutput, ""));

    VideoMetadata metadata = extractor.extract(testFile);

    assertThat(metadata.duration()).isEqualTo(Duration.ofNanos(10_500_000_000L));
    assertThat(metadata.sizeBytes()).isEqualTo(500000L);
    assertThat(metadata.bitrateBps()).isEqualTo(400000L);
    assertThat(metadata.format()).isEqualTo("mov,mp4,m4a,3gp,3g2,mj2");

    assertThat(metadata.videoStream().codec()).isEqualTo("h264");
    assertThat(metadata.videoStream().width()).isEqualTo(1920);
    assertThat(metadata.videoStream().height()).isEqualTo(1080);
    assertThat(metadata.videoStream().frameRate()).isEqualTo(30.0);
    assertThat(metadata.videoStream().aspectRatio()).isEqualTo("16:9");
    assertThat(metadata.videoStream().bitrateBps()).isEqualTo(350000L);

    assertThat(metadata.audioStream()).isPresent();
    assertThat(metadata.audioStream().get().codec()).isEqualTo("aac");
    assertThat(metadata.audioStream().get().channels()).isEqualTo(2);
    assertThat(metadata.audioStream().get().sampleRate()).isEqualTo(48000);
    assertThat(metadata.audioStream().get().bitrateBps()).isEqualTo(50000L);
  }

  @Test
  void shouldThrowExceptionWhenExecutorFails() throws Exception {
    when(executor.execute(anyList())).thenReturn(new CommandResult(1, "", "some-error"));

    assertThatThrownBy(() -> extractor.extract(testFile))
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("ffprobe process exited with code 1")
        .hasMessageContaining("some-error");
  }

  @Test
  void shouldThrowExceptionWhenJsonIsMalformed() throws Exception {
    when(executor.execute(anyList())).thenReturn(new CommandResult(0, "{invalid-json}", ""));

    assertThatThrownBy(() -> extractor.extract(testFile))
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("Failed to parse ffprobe JSON output");
  }
}
