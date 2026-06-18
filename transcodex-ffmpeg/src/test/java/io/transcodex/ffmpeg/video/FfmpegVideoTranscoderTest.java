package io.transcodex.ffmpeg.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.transcodex.core.video.TranscodingException;
import io.transcodex.core.video.TranscodingOptions;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegVideoTranscoderTest {

  private FfmpegExecutor executor;
  private FfmpegVideoTranscoder transcoder;

  @BeforeEach
  void setUp() {
    executor = mock(FfmpegExecutor.class);
    transcoder = new FfmpegVideoTranscoder(executor);
  }

  @Test
  void shouldThrowExceptionIfSourceDoesNotExist(@TempDir Path tempDir) {
    Path source = tempDir.resolve("non-existent.mp4");
    Path target = tempDir.resolve("output.mp4");
    TranscodingOptions options =
        TranscodingOptions.builder().resolution(VideoResolution.P720).build();

    assertThatThrownBy(() -> transcoder.transcode(source, target, options))
        .isInstanceOf(TranscodingException.class)
        .hasMessageContaining("Source file does not exist");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSuccessfullyTranscodeWithCorrectArgs(@TempDir Path tempDir) throws Exception {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path target = tempDir.resolve("output.mp4");
    TranscodingOptions options =
        TranscodingOptions.builder()
            .resolution(VideoResolution.P720)
            .videoCodec("libx264")
            .audioCodec("aac")
            .frameRate(24.0)
            .videoBitrate(2000000L)
            .audioBitrate(128000L)
            .build();

    when(executor.execute(anyList())).thenReturn(new CommandResult(0, "success", ""));

    transcoder.transcode(source, target, options);

    verify(executor)
        .execute(
            argThat(
                cmd -> {
                  assertThat(cmd)
                      .containsExactly(
                          "ffmpeg",
                          "-y",
                          "-i",
                          source.toAbsolutePath().toString(),
                          "-vf",
                          "scale=1280:720",
                          "-c:v",
                          "libx264",
                          "-b:v",
                          "2000000",
                          "-c:a",
                          "aac",
                          "-b:a",
                          "128000",
                          "-r",
                          "24.0",
                          "-preset",
                          "fast",
                          target.toAbsolutePath().toString());
                  return true;
                }));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldThrowTranscodingExceptionOnFfmpegError(@TempDir Path tempDir) throws Exception {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path target = tempDir.resolve("output.mp4");
    TranscodingOptions options =
        TranscodingOptions.builder().resolution(VideoResolution.P720).build();

    when(executor.execute(anyList())).thenReturn(new CommandResult(1, "", "transcode error"));

    assertThatThrownBy(() -> transcoder.transcode(source, target, options))
        .isInstanceOf(TranscodingException.class)
        .hasMessageContaining("exit code 1")
        .hasMessageContaining("transcode error");
  }
}
