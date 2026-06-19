package io.transcodex.core.video;

import java.util.Objects;
import java.util.Optional;

/** Configuration options for video transcoding processes. */
public record TranscodingOptions(
    VideoResolution resolution,
    String videoCodec,
    String audioCodec,
    Double frameRate,
    Long videoBitrate,
    Long audioBitrate,
    boolean gpuAccelerated,
    boolean generateHls,
    int threads,
    Optional<HlsEncryptionConfig> encryptionConfig,
    long timeoutSeconds) {
  public TranscodingOptions {
    Objects.requireNonNull(resolution, "Resolution must not be null");
    Objects.requireNonNull(videoCodec, "Video codec must not be null");
    Objects.requireNonNull(audioCodec, "Audio codec must not be null");
    Objects.requireNonNull(encryptionConfig, "Encryption config wrapper must not be null");
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private VideoResolution resolution;
    private String videoCodec = "h264";
    private String audioCodec = "aac";
    private Double frameRate;
    private Long videoBitrate;
    private Long audioBitrate;
    private boolean gpuAccelerated = false;
    private boolean generateHls = false;
    private int threads = 0;
    private HlsEncryptionConfig encryptionConfig;
    private long timeoutSeconds = 1800; // Default: 30 minutes

    public Builder resolution(VideoResolution resolution) {
      this.resolution = resolution;
      return this;
    }

    public Builder videoCodec(String videoCodec) {
      this.videoCodec = videoCodec;
      return this;
    }

    public Builder audioCodec(String audioCodec) {
      this.audioCodec = audioCodec;
      return this;
    }

    public Builder frameRate(Double frameRate) {
      this.frameRate = frameRate;
      return this;
    }

    public Builder videoBitrate(Long videoBitrate) {
      this.videoBitrate = videoBitrate;
      return this;
    }

    public Builder audioBitrate(Long audioBitrate) {
      this.audioBitrate = audioBitrate;
      return this;
    }

    public Builder gpuAccelerated(boolean gpuAccelerated) {
      this.gpuAccelerated = gpuAccelerated;
      return this;
    }

    public Builder generateHls(boolean generateHls) {
      this.generateHls = generateHls;
      return this;
    }

    public Builder threads(int threads) {
      this.threads = threads;
      return this;
    }

    public Builder encryptionConfig(HlsEncryptionConfig encryptionConfig) {
      this.encryptionConfig = encryptionConfig;
      return this;
    }

    public Builder timeoutSeconds(long timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    public TranscodingOptions build() {
      Objects.requireNonNull(resolution, "Resolution is required to build TranscodingOptions");
      if (videoBitrate == null) {
        videoBitrate = resolution.defaultBitrateBps();
      }
      return new TranscodingOptions(
          resolution,
          videoCodec,
          audioCodec,
          frameRate,
          videoBitrate,
          audioBitrate,
          gpuAccelerated,
          generateHls,
          threads,
          Optional.ofNullable(encryptionConfig),
          timeoutSeconds);
    }
  }
}
