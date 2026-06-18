package io.transcodex.api.video;

import io.transcodex.core.video.VideoException;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResult;

/**
 * Entry facade contract for the TranscodeX-SDK. Coordinates video validation, metadata retrieval,
 * transcoding, thumbnailing, and transferring outputs to the storage provider.
 */
public interface VideoProcessor {

  /**
   * Executes the video processing pipeline.
   *
   * @param request the structured processing parameters.
   * @return the result referencing the generated assets.
   * @throws VideoException if any stage in the pipeline fails.
   */
  VideoResult process(VideoRequest request) throws VideoException;
}
