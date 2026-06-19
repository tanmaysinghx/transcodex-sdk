package io.transcodex.core.metadata;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Aggregated container metadata for a video file. */
public record VideoMetadata(
    Duration duration,
    Long sizeBytes,
    Long bitrateBps,
    String format,
    VideoStreamMetadata videoStream,
    Optional<AudioStreamMetadata> audioStream) {
  public VideoMetadata {
    Objects.requireNonNull(duration, "Duration must not be null");
    Objects.requireNonNull(format, "Format must not be null");
    Objects.requireNonNull(videoStream, "Video stream metadata must not be null");
    Objects.requireNonNull(audioStream, "Audio stream metadata wrapper must not be null");
    if (duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException("Duration must be positive");
    }
    if (sizeBytes != null && sizeBytes <= 0) {
      throw new IllegalArgumentException("Size must be positive");
    }
    if (bitrateBps != null && bitrateBps <= 0) {
      throw new IllegalArgumentException("Bitrate must be positive");
    }
    if (format.isBlank()) {
      throw new IllegalArgumentException("Format must not be blank");
    }
  }
}
