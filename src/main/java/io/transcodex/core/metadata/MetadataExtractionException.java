package io.transcodex.core.metadata;

import io.transcodex.core.video.VideoException;

/** Exception thrown when video metadata extraction operations fail. */
public class MetadataExtractionException extends VideoException {

  public MetadataExtractionException(String message) {
    super(message);
  }

  public MetadataExtractionException(String message, Throwable cause) {
    super(message, cause);
  }
}
