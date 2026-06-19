package io.transcodex.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.transcodex.core.video.VideoResolution;
import org.junit.jupiter.api.Test;

class TranscodexPropertiesTest {

  @Test
  void shouldLoadDefaultProperties() {
    TranscodexProperties props = new TranscodexProperties();
    assertThat(props.getDefaultResolutions())
        .containsExactly(VideoResolution.P360, VideoResolution.P720);
    assertThat(props.isDefaultEncryptChunks()).isFalse();
    assertThat(props.getDefaultEncodingThreads()).isEqualTo(4);
    assertThat(props.isDefaultGenerateHls()).isTrue();
    assertThat(props.isDefaultGenerateThumbnail()).isTrue();
    assertThat(props.getDefaultThumbnailWidth()).isEqualTo(320);
    assertThat(props.getDefaultThumbnailHeight()).isEqualTo(180);
    assertThat(props.getDefaultThumbnailFormat()).isEqualTo("jpg");
    assertThat(props.getDefaultThumbnailPositionSeconds()).isEqualTo(0.5);
  }
}
