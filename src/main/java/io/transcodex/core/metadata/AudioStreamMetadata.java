package io.transcodex.core.metadata;

import java.util.Objects;

/** Metadata details of an audio stream track. */
public record AudioStreamMetadata(String codec, int channels, int sampleRate, Long bitrateBps) {
  public AudioStreamMetadata {
    Objects.requireNonNull(codec, "Codec must not be null");
    if (channels <= 0) {
      throw new IllegalArgumentException("Channels must be positive");
    }
    if (sampleRate <= 0) {
      throw new IllegalArgumentException("Sample rate must be positive");
    }
  }
}
