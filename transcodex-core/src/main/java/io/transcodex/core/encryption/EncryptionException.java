package io.transcodex.core.encryption;

import io.transcodex.core.video.VideoException;

/** Exception thrown when segment encryption operations fail. */
public class EncryptionException extends VideoException {

  public EncryptionException(String message) {
    super(message);
  }

  public EncryptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
