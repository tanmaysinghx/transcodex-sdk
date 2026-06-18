package io.transcodex.core.storage;

import io.transcodex.core.video.VideoException;

/** Exception thrown when asset storage operations fail. */
public class StorageException extends VideoException {

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
