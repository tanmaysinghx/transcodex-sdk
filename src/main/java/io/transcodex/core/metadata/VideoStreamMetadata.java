package io.transcodex.core.metadata;

import java.util.Objects;

/** Metadata details of a video stream track. */
public record VideoStreamMetadata(
    String codec, int width, int height, double frameRate, String aspectRatio, Long bitrateBps) {
  public VideoStreamMetadata {
    Objects.requireNonNull(codec, "Codec must not be null");
    Objects.requireNonNull(aspectRatio, "Aspect ratio must not be null");
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Width and height must be positive");
    }
    if (frameRate <= 0.0) {
      throw new IllegalArgumentException("Frame rate must be positive");
    }
  }
}
