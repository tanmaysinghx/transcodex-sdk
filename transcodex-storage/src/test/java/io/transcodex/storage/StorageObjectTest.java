package io.transcodex.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageObjectTest {

  @Test
  void shouldSuccessfullyBuildStorageObject() throws Exception {
    URI uri = new URI("s3://my-bucket/video.mp4");
    Instant now = Instant.now();
    StorageObject obj =
        new StorageObject("video.mp4", 1024L, "video/mp4", uri, Optional.of("hash-value"), now);

    assertThat(obj.key()).isEqualTo("video.mp4");
    assertThat(obj.sizeBytes()).isEqualTo(1024L);
    assertThat(obj.contentType()).isEqualTo("video/mp4");
    assertThat(obj.uri()).isEqualTo(uri);
    assertThat(obj.checksum()).hasValue("hash-value");
    assertThat(obj.createdAt()).isEqualTo(now);
  }

  @Test
  void shouldThrowExceptionWhenKeyIsBlank() throws Exception {
    URI uri = new URI("s3://my-bucket/video.mp4");
    assertThatThrownBy(
            () -> new StorageObject("", 1024L, "video/mp4", uri, Optional.empty(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Storage key must not be blank");
  }

  @Test
  void shouldThrowExceptionWhenSizeIsNegative() throws Exception {
    URI uri = new URI("s3://my-bucket/video.mp4");
    assertThatThrownBy(
            () ->
                new StorageObject(
                    "video.mp4", -1L, "video/mp4", uri, Optional.empty(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Size must be non-negative");
  }

  @Test
  void shouldThrowExceptionWhenContentTypeIsBlank() throws Exception {
    URI uri = new URI("s3://my-bucket/video.mp4");
    assertThatThrownBy(
            () -> new StorageObject("video.mp4", 1024L, " ", uri, Optional.empty(), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Content type must not be blank");
  }
}
