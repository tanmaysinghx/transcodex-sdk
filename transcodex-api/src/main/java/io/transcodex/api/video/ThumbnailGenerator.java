package io.transcodex.api.video;

import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.TranscodingException;
import java.nio.file.Path;

/** Contract for extracting static image thumbnails from video tracks. */
public interface ThumbnailGenerator {

  /**
   * Captures a single frame thumbnail at the configured position.
   *
   * @param source the source video path.
   * @param target the target output image path.
   * @param options the thumbnail configuration options.
   * @throws TranscodingException if thumbnail extraction fails.
   */
  void generate(Path source, Path target, ThumbnailOptions options) throws TranscodingException;
}
