package io.transcodex.ffmpeg.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.core.metadata.AudioStreamMetadata;
import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.metadata.VideoStreamMetadata;
import io.transcodex.ffmpeg.executor.CommandResult;
import io.transcodex.ffmpeg.executor.FfmpegExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Concrete implementation of MetadataExtractor using the external {@code ffprobe} command-line
 * utility.
 */
public class FfprobeMetadataExtractor implements MetadataExtractor {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final FfmpegExecutor executor;
  private final String ffprobePath;

  /**
   * Constructs an extractor utilizing the default command "ffprobe".
   *
   * @param executor the process executor to delegate to.
   */
  public FfprobeMetadataExtractor(FfmpegExecutor executor) {
    this(executor, "ffprobe");
  }

  /**
   * Constructs an extractor utilizing a custom executable path.
   *
   * @param executor the process executor to delegate to.
   * @param ffprobePath the path/name of the ffprobe executable.
   */
  public FfprobeMetadataExtractor(FfmpegExecutor executor, String ffprobePath) {
    this.executor = Objects.requireNonNull(executor, "Executor must not be null");
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

    try {
      JsonNode root = OBJECT_MAPPER.readTree(result.stdout());

      // Parse container metadata
      JsonNode formatNode = root.path("format");
      if (formatNode.isMissingNode()) {
        throw new MetadataExtractionException("Missing 'format' node in ffprobe JSON output");
      }

      String formatName = formatNode.path("format_name").asText();
      if (formatName.isBlank()) {
        throw new MetadataExtractionException("Blank format_name in media file container format");
      }

      double durationSeconds = formatNode.path("duration").asDouble(-1.0);
      if (durationSeconds <= 0.0) {
        throw new MetadataExtractionException(
            "Invalid duration parsed from media file format: " + durationSeconds);
      }
      Duration duration = Duration.ofNanos((long) (durationSeconds * 1_000_000_000L));

      long sizeBytes = formatNode.path("size").asLong(-1L);
      if (sizeBytes < 0) {
        throw new MetadataExtractionException(
            "Invalid file size parsed from media file format: " + sizeBytes);
      }

      long bitrateBps = formatNode.path("bit_rate").asLong(-1L);

      // Parse streams metadata
      JsonNode streamsNode = root.path("streams");
      if (!streamsNode.isArray() || streamsNode.isEmpty()) {
        throw new MetadataExtractionException("No stream tracks found in media file");
      }

      VideoStreamMetadata videoStream = null;
      AudioStreamMetadata audioStream = null;

      for (JsonNode streamNode : streamsNode) {
        String codecType = streamNode.path("codec_type").asText();
        if ("video".equals(codecType) && videoStream == null) {
          String codec = streamNode.path("codec_name").asText();
          int width = streamNode.path("width").asInt(0);
          int height = streamNode.path("height").asInt(0);

          String rFrameRate = streamNode.path("r_frame_rate").asText();
          if (rFrameRate.isBlank()) {
            rFrameRate = streamNode.path("avg_frame_rate").asText();
          }
          double frameRate = parseFrameRate(rFrameRate);

          String aspectRatio = streamNode.path("display_aspect_ratio").asText();
          if (aspectRatio.isBlank() || "N/A".equals(aspectRatio)) {
            if (width > 0 && height > 0) {
              aspectRatio = computeAspectRatio(width, height);
            } else {
              aspectRatio = "unknown";
            }
          }

          long videoBitrate = streamNode.path("bit_rate").asLong(-1L);
          if (videoBitrate <= 0 && bitrateBps > 0) {
            videoBitrate = bitrateBps;
          }

          videoStream =
              new VideoStreamMetadata(
                  codec,
                  width,
                  height,
                  frameRate,
                  aspectRatio,
                  videoBitrate > 0 ? videoBitrate : null);
        } else if ("audio".equals(codecType) && audioStream == null) {
          String codec = streamNode.path("codec_name").asText();
          int channels = streamNode.path("channels").asInt(0);
          int sampleRate = streamNode.path("sample_rate").asInt(0);
          long audioBitrate = streamNode.path("bit_rate").asLong(-1L);

          audioStream =
              new AudioStreamMetadata(
                  codec, channels, sampleRate, audioBitrate > 0 ? audioBitrate : null);
        }
      }

      if (videoStream == null) {
        throw new MetadataExtractionException("No valid video stream found in media file");
      }

      return new VideoMetadata(
          duration,
          sizeBytes,
          bitrateBps > 0 ? bitrateBps : null,
          formatName,
          videoStream,
          Optional.ofNullable(audioStream));
    } catch (Exception e) {
      if (e instanceof MetadataExtractionException) {
        throw (MetadataExtractionException) e;
      }
      throw new MetadataExtractionException("Failed to parse ffprobe JSON output", e);
    }
  }

  private double parseFrameRate(String frameRateStr) {
    if (frameRateStr == null
        || frameRateStr.isBlank()
        || "0/0".equals(frameRateStr)
        || "N/A".equals(frameRateStr)) {
      return 30.0;
    }
    try {
      if (frameRateStr.contains("/")) {
        String[] parts = frameRateStr.split("/");
        if (parts.length == 2) {
          double num = Double.parseDouble(parts[0]);
          double den = Double.parseDouble(parts[1]);
          if (den != 0.0) {
            return num / den;
          }
        }
      } else {
        return Double.parseDouble(frameRateStr);
      }
    } catch (NumberFormatException e) {
      // Ignore and fallback
    }
    return 30.0;
  }

  private String computeAspectRatio(int width, int height) {
    int gcd = gcd(width, height);
    return (width / gcd) + ":" + (height / gcd);
  }

  private int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
  }
}
