package io.transcodex.core.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class VideoRequestTest {

  @Test
  void shouldBuildRequestSuccessfully() {
    Path source = Paths.get("input.mp4");
    Path outputDir = Paths.get("output");

    VideoRequest request =
        VideoRequest.builder()
            .source(source)
            .outputDir(outputDir)
            .resolution(VideoResolution.P720)
            .generateThumbnail(true)
            .storagePrefix("prefix")
            .build();

    assertThat(request.source()).isEqualTo(source);
    assertThat(request.outputDir()).isEqualTo(outputDir);
    assertThat(request.resolutions()).containsExactly(VideoResolution.P720);
    assertThat(request.generateThumbnail()).isTrue();
    assertThat(request.thumbnailOptions()).isPresent();
    assertThat(request.thumbnailOptions().get().format()).isEqualTo("jpg");
    assertThat(request.storagePrefix()).hasValue("prefix");
  }

  @Test
  void shouldThrowExceptionWhenSourceIsNull() {
    assertThatThrownBy(
            () ->
                VideoRequest.builder()
                    .outputDir(Paths.get("output"))
                    .resolution(VideoResolution.P720)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Source path must not be null");
  }

  @Test
  void shouldThrowExceptionWhenOutputDirIsNull() {
    assertThatThrownBy(
            () ->
                VideoRequest.builder()
                    .source(Paths.get("input.mp4"))
                    .resolution(VideoResolution.P720)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Output directory must not be null");
  }

  @Test
  void shouldThrowExceptionWhenNoOutputsRequested() {
    assertThatThrownBy(
            () ->
                VideoRequest.builder()
                    .source(Paths.get("input.mp4"))
                    .outputDir(Paths.get("output"))
                    .generateThumbnail(false)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "At least one transcoding resolution or thumbnail generation must be requested");
  }
}
