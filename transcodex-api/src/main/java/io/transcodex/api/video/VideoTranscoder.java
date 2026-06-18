package io.transcodex.api.video;

import io.transcodex.core.video.TranscodingException;
import io.transcodex.core.video.TranscodingOptions;
import java.nio.file.Path;

/** Contract for transcoding a single video target. */
public interface VideoTranscoder {

  /**
   * Transcodes a video from the source path to the target path with specified options.
   *
   * @param source the source video path.
   * @param target the target destination path.
   * @param options the transcoding settings.
   * @throws TranscodingException if transcoding fails.
   */
  void transcode(Path source, Path target, TranscodingOptions options) throws TranscodingException;
}
