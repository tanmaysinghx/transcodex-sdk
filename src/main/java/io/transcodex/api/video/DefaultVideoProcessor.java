package io.transcodex.api.video;

import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.api.storage.StorageProvider;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.video.HlsEncryptionConfig;
import io.transcodex.core.video.Thumbnail;
import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.TranscodingOptions;
import io.transcodex.core.video.VideoAsset;
import io.transcodex.core.video.VideoException;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates metadata extraction, thumbnail generation, transcoding, and optional storage. */
public class DefaultVideoProcessor implements VideoProcessor {

  private static final Logger log = LoggerFactory.getLogger(DefaultVideoProcessor.class);
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final MetadataExtractor metadataExtractor;
  private final VideoTranscoder videoTranscoder;
  private final ThumbnailGenerator thumbnailGenerator;
  private final Optional<StorageProvider> storageProvider;
  private final ExecutorService executorService;

  public DefaultVideoProcessor(
      MetadataExtractor metadataExtractor,
      VideoTranscoder videoTranscoder,
      ThumbnailGenerator thumbnailGenerator) {
    this(
        metadataExtractor,
        videoTranscoder,
        thumbnailGenerator,
        Optional.empty(),
        ForkJoinPool.commonPool());
  }

  public DefaultVideoProcessor(
      MetadataExtractor metadataExtractor,
      VideoTranscoder videoTranscoder,
      ThumbnailGenerator thumbnailGenerator,
      StorageProvider storageProvider) {
    this(
        metadataExtractor,
        videoTranscoder,
        thumbnailGenerator,
        Optional.ofNullable(storageProvider),
        ForkJoinPool.commonPool());
  }

  public DefaultVideoProcessor(
      MetadataExtractor metadataExtractor,
      VideoTranscoder videoTranscoder,
      ThumbnailGenerator thumbnailGenerator,
      Optional<StorageProvider> storageProvider,
      ExecutorService executorService) {
    this.metadataExtractor =
        Objects.requireNonNull(metadataExtractor, "metadataExtractor must not be null");
    this.videoTranscoder =
        Objects.requireNonNull(videoTranscoder, "videoTranscoder must not be null");
    this.thumbnailGenerator =
        Objects.requireNonNull(thumbnailGenerator, "thumbnailGenerator must not be null");
    this.storageProvider =
        Objects.requireNonNull(storageProvider, "storageProvider must not be null");
    this.executorService =
        Objects.requireNonNull(executorService, "executorService must not be null");
  }

  @Override
  public VideoResult process(VideoRequest request) throws VideoException {
    Objects.requireNonNull(request, "request must not be null");
    createOutputDirectory(request.outputDir());

    log.info("Processing video: {}", request.source());
    VideoMetadata metadata = metadataExtractor.extract(request.source());
    HlsEncryptionConfig encryption = createEncryptionConfig(request);

    List<CompletableFuture<Void>> tasks = new ArrayList<>();
    List<VideoAsset> assets = Collections.synchronizedList(new ArrayList<>());
    List<Path> temporaryFiles = Collections.synchronizedList(new ArrayList<>());

    Optional<Thumbnail> thumbnail = scheduleThumbnail(request, tasks, temporaryFiles);
    scheduleTranscodes(request, encryption, tasks, assets, temporaryFiles);
    await(tasks);

    Path masterPlaylist = createMasterPlaylistIfNeeded(request, assets);
    List<String> storedKeys =
        uploadIfConfigured(
            request,
            thumbnail,
            assets,
            masterPlaylist,
            encryption == null ? null : encryption.keyFile(),
            temporaryFiles);

    return new VideoResult(
        metadata,
        thumbnail,
        List.copyOf(assets),
        storedKeys,
        Optional.ofNullable(masterPlaylist),
        Optional.ofNullable(encryption).map(HlsEncryptionConfig::keyFile));
  }

  private void createOutputDirectory(Path outputDir) {
    try {
      Files.createDirectories(outputDir);
    } catch (IOException e) {
      throw new VideoException("Failed to create output directory: " + outputDir, e);
    }
  }

  private HlsEncryptionConfig createEncryptionConfig(VideoRequest request) {
    if (!request.encryptChunks() || !request.generateHls()) {
      return null;
    }

    try {
      byte[] key = new byte[16];
      SECURE_RANDOM.nextBytes(key);

      Path keyFile = request.outputDir().resolve("encryption.key");
      Path keyInfoFile = request.outputDir().resolve("key_info.txt");
      Files.write(keyFile, key);
      Files.writeString(keyInfoFile, "encryption.key\n" + keyFile.toAbsolutePath() + "\n");

      return new HlsEncryptionConfig(keyFile, keyInfoFile, "encryption.key");
    } catch (IOException e) {
      throw new VideoException("Failed to generate encryption key", e);
    }
  }

  private Optional<Thumbnail> scheduleThumbnail(
      VideoRequest request, List<CompletableFuture<Void>> tasks, List<Path> temporaryFiles) {
    if (!request.generateThumbnail() || request.thumbnailOptions().isEmpty()) {
      return Optional.empty();
    }

    ThumbnailOptions options = request.thumbnailOptions().orElseThrow();
    Path target =
        request
            .outputDir()
            .resolve("thumbnail_" + System.currentTimeMillis() + "." + options.format());

    tasks.add(
        CompletableFuture.runAsync(
            () -> {
              thumbnailGenerator.generate(request.source(), target, options);
              temporaryFiles.add(target);
            },
            executorService));

    return Optional.of(
        new Thumbnail(
            target,
            options.width(),
            options.height(),
            options.positionSeconds(),
            options.format()));
  }

  private void scheduleTranscodes(
      VideoRequest request,
      HlsEncryptionConfig encryption,
      List<CompletableFuture<Void>> tasks,
      List<VideoAsset> assets,
      List<Path> temporaryFiles) {
    for (VideoResolution resolution : request.resolutions()) {
      Path target = createVideoTarget(request, resolution);
      TranscodingOptions options = createTranscodingOptions(request, resolution, encryption);

      tasks.add(
          CompletableFuture.runAsync(
              () -> {
                videoTranscoder.transcode(request.source(), target, options);
                assets.add(
                    new VideoAsset(target, resolution, fileSize(target), options.videoCodec()));
                temporaryFiles.add(target);
                if (request.generateHls()) {
                  temporaryFiles.addAll(findHlsSegments(target));
                }
              },
              executorService));
    }
  }

  private Path createVideoTarget(VideoRequest request, VideoResolution resolution) {
    String extension = request.generateHls() ? ".m3u8" : ".mp4";
    String filename =
        "transcoded_" + resolution.label() + "_" + System.currentTimeMillis() + extension;
    return request.outputDir().resolve(filename);
  }

  private TranscodingOptions createTranscodingOptions(
      VideoRequest request, VideoResolution resolution, HlsEncryptionConfig encryption) {
    TranscodingOptions.Builder builder =
        TranscodingOptions.builder()
            .resolution(resolution)
            .generateHls(request.generateHls())
            .threads(request.encodingThreads());
    if (encryption != null) {
      builder.encryptionConfig(encryption);
    }
    return builder.build();
  }

  private void await(List<CompletableFuture<Void>> tasks) {
    try {
      CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof VideoException videoException) {
        throw videoException;
      }
      throw new VideoException("Video processing task failed", e.getCause());
    }
  }

  private Path createMasterPlaylistIfNeeded(VideoRequest request, List<VideoAsset> assets) {
    if (!request.generateHls() || assets.isEmpty()) {
      return null;
    }

    try {
      return generateMasterPlaylist(request.outputDir(), assets);
    } catch (IOException e) {
      throw new VideoException("Failed to generate master playlist", e);
    }
  }

  private List<String> uploadIfConfigured(
      VideoRequest request,
      Optional<Thumbnail> thumbnail,
      List<VideoAsset> assets,
      Path masterPlaylist,
      Path encryptionKey,
      List<Path> temporaryFiles) {
    if (storageProvider.isEmpty() || request.storagePrefix().isEmpty()) {
      return List.of();
    }

    StorageProvider provider = storageProvider.orElseThrow();
    String prefix = request.storagePrefix().orElseThrow();
    List<String> storedKeys = new ArrayList<>();

    try {
      thumbnail.ifPresent(value -> store(provider, prefix, value.path(), storedKeys));
      storeIfPresent(provider, prefix, masterPlaylist, storedKeys);
      storeIfPresent(provider, prefix, encryptionKey, storedKeys);

      for (VideoAsset asset : assets) {
        store(provider, prefix, asset.path(), storedKeys);
        if (request.generateHls()) {
          for (Path segment : findHlsSegments(asset.path())) {
            store(provider, prefix, segment, storedKeys);
          }
        }
      }
      return List.copyOf(storedKeys);
    } finally {
      deleteFiles(temporaryFiles);
    }
  }

  private void storeIfPresent(
      StorageProvider provider, String prefix, Path path, List<String> storedKeys) {
    if (path != null) {
      store(provider, prefix, path, storedKeys);
    }
  }

  private void store(StorageProvider provider, String prefix, Path path, List<String> storedKeys) {
    String key = prefix + "/" + path.getFileName();
    provider.store(path, key);
    storedKeys.add(key);
  }

  private List<Path> findHlsSegments(Path playlist) {
    String baseName = playlist.getFileName().toString().replaceFirst("\\.m3u8$", "");
    try (var files = Files.list(playlist.getParent())) {
      return files
          .filter(
              path -> {
                String name = path.getFileName().toString();
                return name.startsWith(baseName) && name.endsWith(".ts");
              })
          .toList();
    } catch (IOException e) {
      log.warn("Failed to list HLS segments for {}", playlist, e);
      return List.of();
    }
  }

  private void deleteFiles(List<Path> paths) {
    for (Path path : paths) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException e) {
        log.warn("Failed to delete temporary file: {}", path, e);
      }
    }
  }

  private long fileSize(Path path) {
    try {
      return Files.size(path);
    } catch (IOException e) {
      return 0L;
    }
  }

  private Path generateMasterPlaylist(Path outputDir, List<VideoAsset> assets) throws IOException {
    List<VideoAsset> sortedAssets = new ArrayList<>(assets);
    sortedAssets.sort(Comparator.comparingInt(asset -> asset.resolution().height()));

    StringBuilder content = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
    for (VideoAsset asset : sortedAssets) {
      VideoResolution resolution = asset.resolution();
      content
          .append("#EXT-X-STREAM-INF:BANDWIDTH=")
          .append(resolution.defaultBitrateBps())
          .append(",RESOLUTION=")
          .append(resolution.width())
          .append("x")
          .append(resolution.height())
          .append("\n")
          .append(asset.path().getFileName())
          .append("\n");
    }

    Path playlist = outputDir.resolve("master.m3u8");
    Files.writeString(playlist, content);
    return playlist;
  }
}
