package io.transcodex.ffmpeg.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FfprobeMetadataExtractorIntegrationTest {

  @Test
  void shouldSuccessfullyExtractMetadataFromRealMp4() throws Exception {
    Path dummyMp4 = Path.of("dummy.mp4");
    if (!Files.exists(dummyMp4)) {
      // Try resolving relative to sub-module directory
      dummyMp4 = Path.of("../dummy.mp4");
    }

    if (Files.exists(dummyMp4)) {
      ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
      FfprobeMetadataExtractor extractor = new FfprobeMetadataExtractor(executor);

      VideoMetadata metadata = extractor.extract(dummyMp4);

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
