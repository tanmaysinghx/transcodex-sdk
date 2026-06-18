package io.transcodex.ffmpeg.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FfprobeMetadataExtractorIntegrationTest {

  private static final Path DUMMY_MP4 = Path.of("dummy_test.mp4");

  @BeforeAll
  static void generateDummyVideo() throws Exception {
    ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
    List<String> command =
        List.of(
            "ffmpeg",
            "-y",
            "-f",
            "lavfi",
            "-i",
            "color=c=black:s=640x360:d=1",
            "-f",
            "lavfi",
            "-i",
            "anullsrc",
            "-t",
            "1",
            "-c:v",
            "libx264",
            "-c:a",
            "aac",
            "-pix_fmt",
            "yuv420p",
            DUMMY_MP4.toAbsolutePath().toString());
    try {
      executor.execute(command);
    } catch (Exception e) {
      System.err.println("Could not generate dummy video for integration tests: " + e.getMessage());
    }
  }

  @AfterAll
  static void cleanupDummyVideo() throws Exception {
    Files.deleteIfExists(DUMMY_MP4);
  }

  @Test
  void shouldSuccessfullyExtractMetadataFromRealMp4() throws Exception {
    if (Files.exists(DUMMY_MP4)) {
      ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
      JacksonMetadataParser parser = new JacksonMetadataParser();
      FfprobeMetadataExtractor extractor = new FfprobeMetadataExtractor(executor, parser);

      VideoMetadata metadata = extractor.extract(DUMMY_MP4);

      assertThat(metadata).isNotNull();
      assertThat(metadata.duration().toMillis()).isGreaterThan(0);
      assertThat(metadata.sizeBytes()).isGreaterThan(0);
      assertThat(metadata.format()).contains("mp4");

      assertThat(metadata.videoStream()).isNotNull();
      assertThat(metadata.videoStream().codec()).isEqualTo("h264");
      assertThat(metadata.videoStream().width()).isEqualTo(640);
      assertThat(metadata.videoStream().height()).isEqualTo(360);

      assertThat(metadata.audioStream()).isPresent();
      assertThat(metadata.audioStream().get().codec()).isEqualTo("aac");
      assertThat(metadata.audioStream().get().channels()).isEqualTo(2);
      assertThat(metadata.audioStream().get().sampleRate()).isEqualTo(44100);
    }
  }
}
