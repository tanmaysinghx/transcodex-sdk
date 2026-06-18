package io.transcodex.core.video;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for AES-128 HLS chunk encryption.
 *
 * @param keyFile Path to the 16-byte AES-128 encryption key file
 * @param keyInfoFile Path to the FFmpeg key_info file (contains key URI + key file path)
 * @param keyUri URI that video players will use to fetch the decryption key
 */
public record HlsEncryptionConfig(Path keyFile, Path keyInfoFile, String keyUri) {
  public HlsEncryptionConfig {
    Objects.requireNonNull(keyFile, "Key file path must not be null");
    Objects.requireNonNull(keyInfoFile, "Key info file path must not be null");
    Objects.requireNonNull(keyUri, "Key URI must not be null");
  }
}
