package io.transcodex.ffmpeg.video;

import io.transcodex.api.video.VideoTranscoder;
import io.transcodex.core.video.TranscodingException;
import io.transcodex.core.video.TranscodingOptions;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** FFmpeg-based implementation of {@link VideoTranscoder}. */
public class FfmpegVideoTranscoder implements VideoTranscoder {

  private final FfmpegExecutor executor;
  private final String ffmpegPath;

  public FfmpegVideoTranscoder(FfmpegExecutor executor) {
    this(executor, "ffmpeg");
  }

  public FfmpegVideoTranscoder(FfmpegExecutor executor, String ffmpegPath) {
    this.executor = Objects.requireNonNull(executor, "executor must not be null");
    this.ffmpegPath = Objects.requireNonNull(ffmpegPath, "ffmpegPath must not be null");
  }

  @Override
  public void transcode(Path source, Path target, TranscodingOptions options)
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
    command.add("-i");
    command.add(source.toAbsolutePath().toString());

    // Scale
    command.add("-vf");
    command.add("scale=" + options.resolution().width() + ":" + options.resolution().height());

    // Video codec
    command.add("-c:v");
    command.add(options.videoCodec());

    // Video bitrate
    if (options.videoBitrate() != null) {
      command.add("-b:v");
      command.add(String.valueOf(options.videoBitrate()));
    }

    // Audio codec
    command.add("-c:a");
    command.add(options.audioCodec());

    // Audio bitrate
    if (options.audioBitrate() != null) {
      command.add("-b:a");
      command.add(String.valueOf(options.audioBitrate()));
    }

    // Frame rate
    if (options.frameRate() != null) {
      command.add("-r");
      command.add(String.valueOf(options.frameRate()));
    }

    // Fast preset for default H.264
    command.add("-preset");
    command.add("fast");

    command.add(target.toAbsolutePath().toString());

    try {
      CommandResult result = executor.execute(command);
      if (!result.success()) {
        throw new TranscodingException(
            "ffmpeg transcode failed with exit code "
                + result.exitCode()
                + ". Error: "
                + result.stderr());
      }
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new TranscodingException("Failed to run ffmpeg for transcoding", e);
    }
  }
}
