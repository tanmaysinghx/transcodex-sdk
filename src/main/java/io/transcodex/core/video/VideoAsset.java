package io.transcodex.core.video;

import java.nio.file.Path;
import java.util.Objects;

/** Represents a processed video variant. */
public record VideoAsset(Path path, VideoResolution resolution, long sizeBytes, String codec) {
  public VideoAsset {
    Objects.requireNonNull(path, "Asset path must not be null");
    Objects.requireNonNull(resolution, "Asset resolution must not be null");
    Objects.requireNonNull(codec, "Asset codec must not be null");

    if (sizeBytes < 0) {
      throw new IllegalArgumentException("Asset size must be non-negative");
    }
    if (codec.isBlank()) {
      throw new IllegalArgumentException("Asset codec must not be blank");
    }
  }
}
