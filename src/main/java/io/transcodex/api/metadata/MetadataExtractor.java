package io.transcodex.api.metadata;

import io.transcodex.core.metadata.MetadataExtractionException;
import io.transcodex.core.metadata.VideoMetadata;
import java.nio.file.Path;

/** Contract for parsing container and stream properties from video sources. */
public interface MetadataExtractor {

  /**
   * Inspects the source file and constructs an immutable metadata report.
   *
   * @param source the source video path.
   * @return the parsed video and stream metadata details.
   * @throws MetadataExtractionException if extraction or inspection fails.
   */
  VideoMetadata extract(Path source) throws MetadataExtractionException;
}
