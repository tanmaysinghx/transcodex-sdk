package io.transcodex.ffmpeg.metadata;

import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Concrete implementation of MetadataExtractor using the external {@code ffprobe} command-line
 * utility.
 */
public class FfprobeMetadataExtractor implements MetadataExtractor {

  private final FfmpegExecutor executor;
  private final MetadataParser parser;
  private final String ffprobePath;

  /**
   * Constructs an extractor utilizing the default command "ffprobe" and a default parser.
   *
   * @param executor the process executor to delegate to.
   * @param parser the parser to translate command outputs.
   */
  public FfprobeMetadataExtractor(FfmpegExecutor executor, MetadataParser parser) {
    this(executor, parser, io.transcodex.ffmpeg.executor.EmbeddedFfmpegResolver.getFfprobePath());
  }

  /**
   * Constructs an extractor utilizing custom executables and parsers.
   *
   * @param executor the process executor to delegate to.
   * @param parser the parser to translate command outputs.
   * @param ffprobePath the path/name of the ffprobe executable.
   */
  public FfprobeMetadataExtractor(
      FfmpegExecutor executor, MetadataParser parser, String ffprobePath) {
    this.executor = Objects.requireNonNull(executor, "Executor must not be null");
    this.parser = Objects.requireNonNull(parser, "Parser must not be null");
    this.ffprobePath = Objects.requireNonNull(ffprobePath, "FFprobe path must not be null");
  }

  @Override
  public VideoMetadata extract(Path source) throws MetadataExtractionException {
    Objects.requireNonNull(source, "Source path must not be null");

    if (!Files.exists(source)) {
      throw new MetadataExtractionException("Source file does not exist: " + source);
    }
    if (!Files.isReadable(source)) {
      throw new MetadataExtractionException("Source file is not readable: " + source);
    }

    List<String> command =
        List.of(
            ffprobePath,
            "-v",
            "error",
            "-show_format",
            "-show_streams",
            "-of",
            "json",
            source.toAbsolutePath().toString());

    CommandResult result;
    try {
      result = executor.execute(command);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new MetadataExtractionException("Failed to execute ffprobe process", e);
    }

    if (!result.success()) {
      throw new MetadataExtractionException(
          "ffprobe process exited with code " + result.exitCode() + ". Error: " + result.stderr());
    }

    return parser.parse(result.stdout());
  }
}
