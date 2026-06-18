package io.transcodex.ffmpeg.metadata;

import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;

/**
 * Strategy contract for parsing raw metadata output (e.g., JSON stdout from ffprobe) into
 * domain-level VideoMetadata.
 */
public interface MetadataParser {

  /**
   * Parses the raw output of a probe command.
   *
   * @param rawOutput the raw stdout content.
   * @return the parsed VideoMetadata domain record.
   * @throws MetadataExtractionException if the output is malformed, missing required fields, or
   *     invalid.
   */
  VideoMetadata parse(String rawOutput) throws MetadataExtractionException;
}
