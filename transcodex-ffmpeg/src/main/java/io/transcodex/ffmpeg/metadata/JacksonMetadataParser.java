package io.transcodex.ffmpeg.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.transcodex.core.metadata.AudioStreamMetadata;
import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.metadata.VideoStreamMetadata;
import java.time.Duration;
import java.util.Optional;

/** Concrete implementation of MetadataParser using the Jackson library. */
public class JacksonMetadataParser implements MetadataParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public VideoMetadata parse(String rawOutput) throws MetadataExtractionException {
    if (rawOutput == null || rawOutput.isBlank()) {
      throw new MetadataExtractionException("Raw output is empty or null");
    }

    try {
      JsonNode root = OBJECT_MAPPER.readTree(rawOutput);

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
          if (codec.isBlank()) {
            throw new MetadataExtractionException("Blank video codec name in stream track");
          }
          int width = streamNode.path("width").asInt(0);
          int height = streamNode.path("height").asInt(0);
          if (width <= 0 || height <= 0) {
            throw new MetadataExtractionException(
                "Invalid video stream dimensions: " + width + "x" + height);
          }

          String rFrameRate = streamNode.path("r_frame_rate").asText();
          if (rFrameRate.isBlank()) {
            rFrameRate = streamNode.path("avg_frame_rate").asText();
          }
          double frameRate = parseFrameRate(rFrameRate);

          String aspectRatio = streamNode.path("display_aspect_ratio").asText();
          if (aspectRatio.isBlank() || "N/A".equals(aspectRatio)) {
            aspectRatio = computeAspectRatio(width, height);
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
          if (codec.isBlank()) {
            throw new MetadataExtractionException("Blank audio codec name in stream track");
          }
          int channels = streamNode.path("channels").asInt(0);
          int sampleRate = streamNode.path("sample_rate").asInt(0);
          if (channels <= 0 || sampleRate <= 0) {
            throw new MetadataExtractionException("Invalid audio stream configuration");
          }
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
