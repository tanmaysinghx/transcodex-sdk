package io.transcodex.api.video;

import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.api.storage.StorageProvider;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.video.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default orchestrator executing the video transcode/thumbnail workflow. */
public class DefaultVideoProcessor implements VideoProcessor {

  private static final Logger log = LoggerFactory.getLogger(DefaultVideoProcessor.class);

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
    log.info("Starting video processing pipeline for source: {}", request.source());

    try {
      Files.createDirectories(request.outputDir());
    } catch (IOException e) {
      throw new VideoException("Failed to create output directory: " + request.outputDir(), e);
    }

    // 1. Extract Metadata
    VideoMetadata metadata = metadataExtractor.extract(request.source());
    log.info(
        "Successfully extracted metadata: duration={}s, format={}",
        metadata.duration().toSeconds(),
        metadata.format());

    // 2. Generate AES-128 encryption key if requested
    HlsEncryptionConfig encryptionConfig = null;
    Path encryptionKeyFile = null;
    if (request.encryptChunks() && request.generateHls()) {
      try {
        encryptionConfig = generateEncryptionKey(request.outputDir());
        encryptionKeyFile = encryptionConfig.keyFile();
        log.info("Generated AES-128 encryption key at: {}", encryptionKeyFile);
      } catch (IOException e) {
        throw new VideoException("Failed to generate encryption key", e);
      }
    }

    List<CompletableFuture<Void>> tasks = new ArrayList<>();
    List<VideoAsset> transcodedAssets = Collections.synchronizedList(new ArrayList<>());
    List<Path> filesToClean = Collections.synchronizedList(new ArrayList<>());

    // 3. Generate Thumbnail (Async/Parallel)
    Optional<Thumbnail> thumbnail;
    if (request.generateThumbnail() && request.thumbnailOptions().isPresent()) {
      ThumbnailOptions thumbOpts = request.thumbnailOptions().get();
      String thumbFilename = "thumbnail_" + System.currentTimeMillis() + "." + thumbOpts.format();
      Path targetThumb = request.outputDir().resolve(thumbFilename);

      tasks.add(
          CompletableFuture.runAsync(
              () -> {
                log.info("Generating thumbnail at position {}s", thumbOpts.positionSeconds());
                thumbnailGenerator.generate(request.source(), targetThumb, thumbOpts);
                filesToClean.add(targetThumb);
              },
              executorService));
      thumbnail =
          Optional.of(
              new Thumbnail(
                  targetThumb,
                  thumbOpts.width(),
                  thumbOpts.height(),
                  thumbOpts.positionSeconds(),
                  thumbOpts.format()));
    } else {
      thumbnail = Optional.empty();
    }

    // 4. Transcode Video Variants (Async/Parallel — one virtual thread per resolution)
    final HlsEncryptionConfig finalEncryptionConfig = encryptionConfig;
    for (VideoResolution resolution : request.resolutions()) {
      String ext = request.generateHls() ? ".m3u8" : ".mp4";
      String videoFilename =
          "transcoded_" + resolution.label() + "_" + System.currentTimeMillis() + ext;
      Path targetVideo = request.outputDir().resolve(videoFilename);

      TranscodingOptions.Builder transOptsBuilder =
          TranscodingOptions.builder()
              .resolution(resolution)
              .generateHls(request.generateHls())
              .threads(request.encodingThreads());

      if (finalEncryptionConfig != null) {
        transOptsBuilder.encryptionConfig(finalEncryptionConfig);
      }

      TranscodingOptions transOpts = transOptsBuilder.build();

      tasks.add(
          CompletableFuture.runAsync(
              () -> {
                log.info("Transcoding video to resolution {}", resolution.label());
                videoTranscoder.transcode(request.source(), targetVideo, transOpts);
                long size;
                try {
                  size = Files.size(targetVideo);
                } catch (IOException e) {
                  size = 0L;
                }
                transcodedAssets.add(
                    new VideoAsset(targetVideo, resolution, size, transOpts.videoCodec()));
                filesToClean.add(targetVideo);

                if (request.generateHls()) {
                  try (var stream = Files.list(request.outputDir())) {
                    stream
                        .filter(
                            p ->
                                p.getFileName().toString().endsWith(".ts")
                                    && p.getFileName()
                                        .toString()
                                        .startsWith("transcoded_" + resolution.label()))
                        .forEach(filesToClean::add);
                  } catch (IOException ignored) {
                  }
                }
              },
              executorService));
    }

    // Wait for all tasks to complete
    try {
      CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof VideoException) {
        throw (VideoException) e.getCause();
      }
      throw new VideoException("Error during parallel transcoding/thumbnail tasks", e.getCause());
    }

    // 5. Generate Master Adaptive Playlist
    Path masterPlaylist = null;
    if (request.generateHls() && transcodedAssets.size() > 0) {
      try {
        masterPlaylist = generateMasterPlaylist(request.outputDir(), transcodedAssets);
        log.info("Generated master adaptive playlist: {}", masterPlaylist);
      } catch (IOException e) {
        throw new VideoException("Failed to generate master playlist", e);
      }
    }

    // 6. Upload Assets to Storage if storageProvider and storagePrefix are present
    List<String> storedKeys = new ArrayList<>();
    if (storageProvider.isPresent() && request.storagePrefix().isPresent()) {
      String prefix = request.storagePrefix().get();
      log.info("Uploading assets to storage with prefix: {}", prefix);

      StorageProvider provider = storageProvider.get();

      try {
        // Upload thumbnail
        if (thumbnail.isPresent()) {
          Path thumbFile = thumbnail.get().path();
          String key = prefix + "/" + thumbFile.getFileName().toString();
          provider.store(thumbFile, key);
          storedKeys.add(key);
        }

        // Upload master playlist
        if (masterPlaylist != null) {
          String key = prefix + "/" + masterPlaylist.getFileName().toString();
          provider.store(masterPlaylist, key);
          storedKeys.add(key);
        }

        // Upload encryption key
        if (encryptionKeyFile != null) {
          String key = prefix + "/" + encryptionKeyFile.getFileName().toString();
          provider.store(encryptionKeyFile, key);
          storedKeys.add(key);
        }

        // Upload video variants
        for (VideoAsset asset : transcodedAssets) {
          Path videoFile = asset.path();
          String key = prefix + "/" + videoFile.getFileName().toString();
          provider.store(videoFile, key);
          storedKeys.add(key);

          if (request.generateHls()) {
            String baseName = videoFile.getFileName().toString().replace(".m3u8", "");
            try (var stream = Files.list(videoFile.getParent())) {
              List<Path> tsFiles =
                  stream
                      .filter(
                          p ->
                              p.getFileName().toString().endsWith(".ts")
                                  && p.getFileName().toString().startsWith(baseName))
                      .toList();
              for (Path tsFile : tsFiles) {
                String tsKey = prefix + "/" + tsFile.getFileName().toString();
                provider.store(tsFile, tsKey);
                storedKeys.add(tsKey);
              }
            } catch (IOException ignored) {
            }
          }
        }
      } finally {
        // Post-upload cleanup of local files
        log.info("Cleaning up temporary local files after upload");
        for (Path path : filesToClean) {
          try {
            Files.deleteIfExists(path);
          } catch (IOException e) {
            log.warn("Failed to delete temporary file: {}", path, e);
          }
        }
      }
    }

    log.info("Video processing pipeline finished successfully");
    return new VideoResult(
        metadata,
        thumbnail,
        List.copyOf(transcodedAssets),
        List.copyOf(storedKeys),
        Optional.ofNullable(masterPlaylist),
        Optional.ofNullable(encryptionKeyFile));
  }

  /**
   * Generates a random 16-byte AES-128 encryption key and a key_info file for FFmpeg.
   *
   * @param outputDir directory to write key files into
   * @return the encryption configuration
   */
  private HlsEncryptionConfig generateEncryptionKey(Path outputDir) throws IOException {
    // Generate 16-byte random AES key
    byte[] keyBytes = new byte[16];
    new SecureRandom().nextBytes(keyBytes);

    Path keyFile = outputDir.resolve("encryption.key");
    Files.write(keyFile, keyBytes);

    // The key URI is a placeholder — the caller (playground/app) sets the real URI
    // by rewriting the .m3u8 playlist or using a proxy. For FFmpeg, we use a relative path.
    String keyUri = "encryption.key";

    // Create key_info file:
    // Line 1: Key URI (what players request)
    // Line 2: Path to key file (what FFmpeg reads during encoding)
    Path keyInfoFile = outputDir.resolve("key_info.txt");
    String keyInfoContent =
        keyUri + "\n" + keyFile.toAbsolutePath().toString() + "\n";
    Files.writeString(keyInfoFile, keyInfoContent);

    return new HlsEncryptionConfig(keyFile, keyInfoFile, keyUri);
  }

  /**
   * Generates a master HLS playlist (master.m3u8) referencing all resolution variant playlists.
   *
   * @param outputDir directory containing the variant playlists
   * @param assets list of transcoded video assets
   * @return path to the master playlist
   */
  private Path generateMasterPlaylist(Path outputDir, List<VideoAsset> assets) throws IOException {
    StringBuilder sb = new StringBuilder();
    sb.append("#EXTM3U\n");
    sb.append("#EXT-X-VERSION:3\n");

    // Sort by resolution height for proper ordering
    List<VideoAsset> sorted = new ArrayList<>(assets);
    sorted.sort(Comparator.comparingInt(a -> a.resolution().height()));

    for (VideoAsset asset : sorted) {
      VideoResolution res = asset.resolution();
      long bandwidth = res.defaultBitrateBps();
      String variantFilename = asset.path().getFileName().toString();

      sb.append("#EXT-X-STREAM-INF:BANDWIDTH=")
          .append(bandwidth)
          .append(",RESOLUTION=")
          .append(res.width())
          .append("x")
          .append(res.height())
          .append("\n");
      sb.append(variantFilename).append("\n");
    }

    Path masterPlaylist = outputDir.resolve("master.m3u8");
    Files.writeString(masterPlaylist, sb.toString());
    return masterPlaylist;
  }
}
