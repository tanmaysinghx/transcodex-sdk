package io.transcodex.core.video;

import java.nio.file.Path;
import java.util.Objects;

/** Represents a generated video thumbnail. */
public record Thumbnail(Path path, int width, int height, double positionSeconds, String format) {
  public Thumbnail {
    Objects.requireNonNull(path, "Thumbnail path must not be null");
    Objects.requireNonNull(format, "Thumbnail format must not be null");

    if (width <= 0) {
      throw new IllegalArgumentException("Thumbnail width must be greater than zero");
    }
    if (height <= 0) {
      throw new IllegalArgumentException("Thumbnail height must be greater than zero");
    }
    if (positionSeconds < 0) {
      throw new IllegalArgumentException("Thumbnail position must be non-negative");
    }
    if (format.isBlank()) {
      throw new IllegalArgumentException("Thumbnail format must not be blank");
    }
  }
}
