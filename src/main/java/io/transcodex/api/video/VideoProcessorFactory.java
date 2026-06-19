package io.transcodex.api.video;

import io.transcodex.ffmpeg.executor.EmbeddedFfmpegResolver;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import io.transcodex.ffmpeg.video.FfmpegThumbnailGenerator;
import io.transcodex.ffmpeg.video.FfmpegVideoTranscoder;

/**
 * Factory class to easily construct a configured {@link VideoProcessor} in plain Java environments
 * without using Spring Boot.
 */
public final class VideoProcessorFactory {

  private VideoProcessorFactory() {}

  /**
   * Creates a default {@link VideoProcessor} implementation, resolving the embedded FFmpeg and
   * FFprobe binaries automatically on Windows.
   *
   * @return the configured VideoProcessor instance.
   */
  public static VideoProcessor createDefault() {
    EmbeddedFfmpegResolver.resolve();

    ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
    JacksonMetadataParser parser = new JacksonMetadataParser();

    FfprobeMetadataExtractor extractor = new FfprobeMetadataExtractor(
        executor, parser, EmbeddedFfmpegResolver.getFfprobePath());
    FfmpegVideoTranscoder transcoder = new FfmpegVideoTranscoder(
        executor, EmbeddedFfmpegResolver.getFfmpegPath());
    FfmpegThumbnailGenerator generator = new FfmpegThumbnailGenerator(
        executor, EmbeddedFfmpegResolver.getFfmpegPath());

    return new DefaultVideoProcessor(extractor, transcoder, generator);
  }
}
