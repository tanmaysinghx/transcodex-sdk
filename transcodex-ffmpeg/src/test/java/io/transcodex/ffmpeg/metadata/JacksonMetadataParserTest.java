package io.transcodex.ffmpeg.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JacksonMetadataParserTest {

  private JacksonMetadataParser parser;

  @BeforeEach
  void setUp() {
    parser = new JacksonMetadataParser();
  }

  @Test
  void shouldSuccessfullyParseCompleteVideoAndAudioJson() {
    String json =
        """
        {
          "format": {
            "format_name": "mp4",
            "duration": "5.500000",
            "size": "100000",
            "bit_rate": "80000"
          },
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "h264",
              "width": 1280,
              "height": 720,
              "r_frame_rate": "60/1",
              "display_aspect_ratio": "16:9",
              "bit_rate": "70000"
            },
            {
              "codec_type": "audio",
              "codec_name": "aac",
              "channels": 2,
              "sample_rate": 44100,
              "bit_rate": "10000"
            }
          ]
        }
        """;

    VideoMetadata metadata = parser.parse(json);

    assertThat(metadata.duration()).isEqualTo(Duration.ofNanos(5_500_000_000L));
    assertThat(metadata.sizeBytes()).isEqualTo(100000L);
    assertThat(metadata.bitrateBps()).isEqualTo(80000L);
    assertThat(metadata.format()).isEqualTo("mp4");

    assertThat(metadata.videoStream().codec()).isEqualTo("h264");
    assertThat(metadata.videoStream().width()).isEqualTo(1280);
    assertThat(metadata.videoStream().height()).isEqualTo(720);
    assertThat(metadata.videoStream().frameRate()).isEqualTo(60.0);
    assertThat(metadata.videoStream().aspectRatio()).isEqualTo("16:9");
    assertThat(metadata.videoStream().bitrateBps()).isEqualTo(70000L);

    assertThat(metadata.audioStream()).isPresent();
    assertThat(metadata.audioStream().get().codec()).isEqualTo("aac");
    assertThat(metadata.audioStream().get().channels()).isEqualTo(2);
    assertThat(metadata.audioStream().get().sampleRate()).isEqualTo(44100);
    assertThat(metadata.audioStream().get().bitrateBps()).isEqualTo(10000L);
  }

  @Test
  void shouldParseVideoOnlySuccessfully() {
    String json =
        """
        {
          "format": {
            "format_name": "mov",
            "duration": "12.000000",
            "size": "250000"
          },
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "hevc",
              "width": 1920,
              "height": 1080,
              "r_frame_rate": "24/1"
            }
          ]
        }
        """;

    VideoMetadata metadata = parser.parse(json);

    assertThat(metadata.format()).isEqualTo("mov");
    assertThat(metadata.audioStream()).isEmpty();
    assertThat(metadata.videoStream().codec()).isEqualTo("hevc");
    assertThat(metadata.videoStream().width()).isEqualTo(1920);
    assertThat(metadata.videoStream().height()).isEqualTo(1080);
    assertThat(metadata.videoStream().aspectRatio()).isEqualTo("16:9"); // computed via GCD
  }

  @Test
  void shouldThrowExceptionWhenJsonIsMalformed() {
    assertThatThrownBy(() -> parser.parse("{{invalid"))
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("Failed to parse ffprobe JSON output");
  }

  @Test
  void shouldThrowExceptionWhenFormatNodeIsMissing() {
    String json = "{\"streams\":[]}";
    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("Missing 'format' node");
  }

  @Test
  void shouldThrowExceptionWhenNoVideoStreamFound() {
    String json =
        """
        {
          "format": {
            "format_name": "mp3",
            "duration": "10.000000",
            "size": "50000"
          },
          "streams": [
            {
              "codec_type": "audio",
              "codec_name": "mp3",
              "channels": 2,
              "sample_rate": 44100
            }
          ]
        }
        """;

    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOf(MetadataExtractionException.class)
        .hasMessageContaining("No valid video stream found in media file");
  }
}
