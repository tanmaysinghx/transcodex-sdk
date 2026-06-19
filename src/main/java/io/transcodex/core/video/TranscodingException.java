package io.transcodex.core.video;

/** Exception thrown when video transcoding operations fail. */
public class TranscodingException extends VideoException {

  public TranscodingException(String message) {
    super(message);
  }

  public TranscodingException(String message, Throwable cause) {
    super(message, cause);
  }
}
