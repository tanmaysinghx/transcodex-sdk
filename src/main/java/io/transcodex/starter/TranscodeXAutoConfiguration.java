package io.transcodex.starter;

import io.transcodex.api.video.DefaultVideoProcessor;
import io.transcodex.api.video.VideoProcessor;
import io.transcodex.core.config.TranscodexProperties;
import io.transcodex.ffmpeg.executor.EmbeddedFfmpegResolver;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import io.transcodex.ffmpeg.video.FfmpegThumbnailGenerator;
import io.transcodex.ffmpeg.video.FfmpegVideoTranscoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration class that registers a default {@link VideoProcessor} bean and
 * exposes SDK settings as {@link TranscodexProperties}.
 */
@AutoConfiguration
@EnableConfigurationProperties(TranscodeXAutoConfiguration.SpringTranscodexProperties.class)
public class TranscodeXAutoConfiguration {

  @ConfigurationProperties(prefix = "transcodex")
  public static class SpringTranscodexProperties extends TranscodexProperties {
    // Inherits all default configurations and maps to "transcodex" in Spring application properties
  }

  @Bean
  @ConditionalOnMissingBean
  public TranscodexProperties transcodexProperties(SpringTranscodexProperties springProperties) {
    return springProperties;
  }

  @Bean
  @ConditionalOnMissingBean
  public VideoProcessor videoProcessor(TranscodexProperties properties) {
    EmbeddedFfmpegResolver.resolve();

    ProcessBuilderExecutor executor = new ProcessBuilderExecutor();
    JacksonMetadataParser parser = new JacksonMetadataParser();

    FfprobeMetadataExtractor extractor =
        new FfprobeMetadataExtractor(executor, parser, EmbeddedFfmpegResolver.getFfprobePath());
    FfmpegVideoTranscoder transcoder =
        new FfmpegVideoTranscoder(executor, EmbeddedFfmpegResolver.getFfmpegPath());
    FfmpegThumbnailGenerator generator =
        new FfmpegThumbnailGenerator(executor, EmbeddedFfmpegResolver.getFfmpegPath());

    return new DefaultVideoProcessor(extractor, transcoder, generator);
  }
}
