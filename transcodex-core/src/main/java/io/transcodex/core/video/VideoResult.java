package io.transcodex.core.video;

import io.transcodex.core.metadata.VideoMetadata;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result output containing references to all produced assets. */
public record VideoResult(
    VideoMetadata metadata,
    Optional<Thumbnail> thumbnail,
    List<VideoAsset> transcodedAssets,
    List<String> storedAssetKeys) {
  public VideoResult {
    Objects.requireNonNull(metadata, "Metadata must not be null");
    Objects.requireNonNull(thumbnail, "Thumbnail wrapper must not be null");
    Objects.requireNonNull(transcodedAssets, "Transcoded assets list must not be null");
    Objects.requireNonNull(storedAssetKeys, "Stored asset keys list must not be null");
  }
}
