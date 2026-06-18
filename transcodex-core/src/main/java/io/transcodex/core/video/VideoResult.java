package io.transcodex.core.video;

import io.transcodex.core.metadata.VideoMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Result output containing references to all produced assets. */
public record VideoResult(
    VideoMetadata metadata,
    Optional<Path> thumbnailPath,
    Map<VideoResolution, Path> transcodedVideos,
    List<String> storedAssetKeys) {
  public VideoResult {
    Objects.requireNonNull(metadata, "Metadata must not be null");
    Objects.requireNonNull(thumbnailPath, "Thumbnail path wrapper must not be null");
    Objects.requireNonNull(transcodedVideos, "Transcoded videos map must not be null");
    Objects.requireNonNull(storedAssetKeys, "Stored asset keys list must not be null");
  }
}
