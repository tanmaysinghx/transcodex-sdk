package io.transcodex.core.video;

import java.util.Objects;

/** Options configuration for generating thumbnails. */
public record ThumbnailOptions(int width, int height, double positionSeconds, String format) {
  public ThumbnailOptions {
    if (width <= 0) {
      throw new IllegalArgumentException("Width must be greater than zero");
    }
    if (height <= 0) {
      throw new IllegalArgumentException("Height must be greater than zero");
    }
    if (positionSeconds < 0.0) {
      throw new IllegalArgumentException("Position in seconds must be non-negative");
    }
    Objects.requireNonNull(format, "Format must not be null");
  }
}
