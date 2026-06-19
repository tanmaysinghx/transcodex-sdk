package io.transcodex.core.video;

/** Represents the processing states in a video transcoding request lifecycle. */
public enum ProcessingStatus {
  SUBMITTED,
  EXTRACTING_METADATA,
  PROCESSING,
  UPLOADING,
  COMPLETED,
  FAILED
}
