package io.transcodex.core.video;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Encapsulates the request options for the transcoding pipeline. */
public record VideoRequest(
    Path source,
    Path outputDir,
    List<VideoResolution> resolutions,
    boolean generateThumbnail,
    Optional<ThumbnailOptions> thumbnailOptions,
    Optional<String> storagePrefix,
    boolean generateHls,
    boolean encryptChunks,
    int encodingThreads) {
  public VideoRequest {
    Objects.requireNonNull(source, "Source path must not be null");
    Objects.requireNonNull(outputDir, "Output directory must not be null");
    Objects.requireNonNull(resolutions, "Resolutions list must not be null");
    Objects.requireNonNull(thumbnailOptions, "Thumbnail options wrapper must not be null");
    Objects.requireNonNull(storagePrefix, "Storage prefix wrapper must not be null");

    if (resolutions.isEmpty() && !generateThumbnail) {
      throw new IllegalArgumentException(
          "At least one transcoding resolution or thumbnail generation must be requested");
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(io.transcodex.core.config.TranscodexProperties config) {
    Objects.requireNonNull(config, "config must not be null");
    Builder builder = new Builder();
    builder.resolutions(config.getDefaultResolutions());
    builder.generateHls(config.isDefaultGenerateHls());
    builder.encryptChunks(config.isDefaultEncryptChunks());
    builder.encodingThreads(config.getDefaultEncodingThreads());
    builder.generateThumbnail(config.isDefaultGenerateThumbnail());
    builder.thumbnailOptions(new ThumbnailOptions(
        config.getDefaultThumbnailWidth(),
        config.getDefaultThumbnailHeight(),
        config.getDefaultThumbnailPositionSeconds(),
        config.getDefaultThumbnailFormat()
    ));
    return builder;
  }

  public static class Builder {
    private Path source;
    private Path outputDir;
    private final List<VideoResolution> resolutions = new ArrayList<>();
    private boolean generateThumbnail = false;
    private ThumbnailOptions thumbnailOptions;
    private String storagePrefix;
    private boolean generateHls = false;
    private boolean encryptChunks = false;
    private int encodingThreads = 0;

    public Builder source(Path source) {
      this.source = source;
      return this;
    }

    public Builder outputDir(Path outputDir) {
      this.outputDir = outputDir;
      return this;
    }

    public Builder resolution(VideoResolution resolution) {
      if (resolution != null) {
        this.resolutions.add(resolution);
      }
      return this;
    }

    public Builder resolutions(List<VideoResolution> resolutions) {
      if (resolutions != null) {
        this.resolutions.addAll(resolutions);
      }
      return this;
    }

    public Builder generateThumbnail(boolean generateThumbnail) {
      this.generateThumbnail = generateThumbnail;
      return this;
    }

    public Builder thumbnailOptions(ThumbnailOptions thumbnailOptions) {
      this.thumbnailOptions = thumbnailOptions;
      return this;
    }

    public Builder storagePrefix(String storagePrefix) {
      this.storagePrefix = storagePrefix;
      return this;
    }

    public Builder generateHls(boolean generateHls) {
      this.generateHls = generateHls;
      return this;
    }

    public Builder encryptChunks(boolean encryptChunks) {
      this.encryptChunks = encryptChunks;
      return this;
    }

    public Builder encodingThreads(int encodingThreads) {
      this.encodingThreads = encodingThreads;
      return this;
    }

    public VideoRequest build() {
      Objects.requireNonNull(source, "Source path must not be null");
      Objects.requireNonNull(outputDir, "Output directory must not be null");

      if (generateThumbnail && thumbnailOptions == null) {
        // Default thumbnail: 640x360 at 1.0s in JPG format
        thumbnailOptions = new ThumbnailOptions(640, 360, 1.0, "jpg");
      }

      // If encryption is requested, HLS must be enabled
      if (encryptChunks) {
        generateHls = true;
      }

      return new VideoRequest(
          source,
          outputDir,
          List.copyOf(resolutions),
          generateThumbnail,
          Optional.ofNullable(thumbnailOptions),
          Optional.ofNullable(storagePrefix),
          generateHls,
          encryptChunks,
          encodingThreads);
    }
  }
}
