package io.transcodex.ffmpeg.video;

import io.transcodex.api.video.ThumbnailGenerator;
import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.TranscodingException;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** FFmpeg-based implementation of {@link ThumbnailGenerator}. */
public class FfmpegThumbnailGenerator implements ThumbnailGenerator {

  private final FfmpegExecutor executor;
  private final String ffmpegPath;

  public FfmpegThumbnailGenerator(FfmpegExecutor executor) {
    this(executor, io.transcodex.ffmpeg.executor.EmbeddedFfmpegResolver.getFfmpegPath());
  }

  public FfmpegThumbnailGenerator(FfmpegExecutor executor, String ffmpegPath) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.ffmpegPath = Objects.requireNonNull(ffmpegPath, "ffmpegPath must not be null");
  }

  @Override
  public void generate(Path source, Path target, ThumbnailOptions options)
      throws TranscodingException {
    Objects.requireNonNull(source, "source path must not be null");
    Objects.requireNonNull(target, "target path must not be null");
    Objects.requireNonNull(options, "options must not be null");

    if (!Files.exists(source)) {
      throw new TranscodingException("Source file does not exist: " + source);
    }

    List<String> command = new ArrayList<>();
    command.add(ffmpegPath);
    command.add("-y");
    command.add("-ss");
    command.add(String.valueOf(options.positionSeconds()));
    command.add("-i");
    command.add(source.toAbsolutePath().toString());
    command.add("-vframes");
    command.add("1");
    command.add("-vf");
    command.add("scale=" + options.width() + ":" + options.height());
    command.add("-f");
    command.add("image2");
    command.add(target.toAbsolutePath().toString());

    try {
      CommandResult result = executor.execute(command);
      if (!result.success()) {
        throw new TranscodingException(
            "ffmpeg thumbnail extraction failed with exit code "
                + result.exitCode()
                + ". Error: "
                + result.stderr());
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new TranscodingException("Failed to run ffmpeg for thumbnail generation", e);
    }
  }
}
