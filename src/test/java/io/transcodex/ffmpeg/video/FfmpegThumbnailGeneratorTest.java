package io.transcodex.ffmpeg.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.TranscodingException;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegThumbnailGeneratorTest {

  private FfmpegExecutor executor;
  private FfmpegThumbnailGenerator generator;

  @BeforeEach
  void setUp() {
    executor = mock(FfmpegExecutor.class);
    generator = new FfmpegThumbnailGenerator(executor, "ffmpeg");
  }

  @Test
  void shouldThrowExceptionIfSourceDoesNotExist(@TempDir Path tempDir) {
    Path source = tempDir.resolve("non-existent.mp4");
    Path target = tempDir.resolve("thumb.jpg");
    ThumbnailOptions options = new ThumbnailOptions(320, 240, 5.0, "jpg");

    assertThatThrownBy(() -> generator.generate(source, target, options))
        .isInstanceOf(TranscodingException.class)
        .hasMessageContaining("Source file does not exist");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSuccessfullyGenerateThumbnail(@TempDir Path tempDir) throws Exception {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path target = tempDir.resolve("thumb.jpg");
    ThumbnailOptions options = new ThumbnailOptions(320, 240, 5.0, "jpg");

    when(executor.execute(anyList())).thenReturn(new CommandResult(0, "success", ""));

    generator.generate(source, target, options);

    verify(executor)
        .execute(
            argThat(
                cmd -> {
                  assertThat(cmd)
                      .containsExactly(
                          "ffmpeg",
                          "-y",
                          "-ss",
                          "5.0",
                          "-i",
                          source.toAbsolutePath().toString(),
                          "-vframes",
                          "1",
                          "-vf",
                          "scale=320:240",
                          "-f",
                          "image2",
                          target.toAbsolutePath().toString());
                  return true;
                }));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldThrowTranscodingExceptionOnFfmpegError(@TempDir Path tempDir) throws Exception {
    Path source = tempDir.resolve("input.mp4");
    Files.createFile(source);
    Path target = tempDir.resolve("thumb.jpg");
    ThumbnailOptions options = new ThumbnailOptions(320, 240, 5.0, "jpg");

    when(executor.execute(anyList())).thenReturn(new CommandResult(1, "", "some error"));

    assertThatThrownBy(() -> generator.generate(source, target, options))
        .isInstanceOf(TranscodingException.class)
        .hasMessageContaining("exit code 1")
        .hasMessageContaining("some error");
  }
}
