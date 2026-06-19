package io.transcodex.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Represents metadata and reference information of an asset in a storage provider. */
public record StorageObject(
    String key,
    Long sizeBytes,
    String contentType,
    URI uri,
    Optional<String> checksum,
    Instant createdAt) {

  public StorageObject {
    Objects.requireNonNull(key, "Storage key must not be null");
    Objects.requireNonNull(contentType, "Content type must not be null");
    Objects.requireNonNull(uri, "URI must not be null");
    Objects.requireNonNull(checksum, "Checksum wrapper must not be null");
    Objects.requireNonNull(createdAt, "Creation timestamp must not be null");

    if (key.isBlank()) {
      throw new IllegalArgumentException("Storage key must not be blank");
    }
    if (sizeBytes != null && sizeBytes < 0) {
      throw new IllegalArgumentException("Size must be non-negative");
    }
    if (contentType.isBlank()) {
      throw new IllegalArgumentException("Content type must not be blank");
    }
  }
}
