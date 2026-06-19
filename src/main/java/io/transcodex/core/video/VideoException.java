package io.transcodex.core.video;

/**
 * Base exception for all TranscodeX video SDK operations. This is a RuntimeException to encourage
 * clean caller interfaces.
 */
public class VideoException extends RuntimeException {

  public VideoException(String message) {
    super(message);
  }

  public VideoException(String message, Throwable cause) {
    super(message, cause);
  }
}
