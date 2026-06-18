package io.transcodex.api.video;

import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.api.storage.StorageProvider;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.video.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    List<CompletableFuture<Void>> tasks = new ArrayList<>();
    Map<VideoResolution, Path> transcodedVideos = new ConcurrentHashMap<>();
    List<Path> filesToClean = Collections.synchronizedList(new ArrayList<>());

    // 2. Generate Thumbnail (Async/Parallel)
    Optional<Path> thumbnailPath;
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
      thumbnailPath = Optional.of(targetThumb);
    } else {
      thumbnailPath = Optional.empty();
    }

    // 3. Transcode Video Variants (Async/Parallel)
    for (VideoResolution resolution : request.resolutions()) {
      String videoFilename =
          "transcoded_" + resolution.label() + "_" + System.currentTimeMillis() + ".mp4";
      Path targetVideo = request.outputDir().resolve(videoFilename);

      TranscodingOptions transOpts = TranscodingOptions.builder().resolution(resolution).build();

      tasks.add(
          CompletableFuture.runAsync(
              () -> {
                log.info("Transcoding video to resolution {}", resolution.label());
                videoTranscoder.transcode(request.source(), targetVideo, transOpts);
                transcodedVideos.put(resolution, targetVideo);
                filesToClean.add(targetVideo);
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

    // 4. Upload Assets to Storage if storageProvider and storagePrefix are present
    List<String> storedKeys = new ArrayList<>();
    if (storageProvider.isPresent() && request.storagePrefix().isPresent()) {
      String prefix = request.storagePrefix().get();
      log.info("Uploading assets to storage with prefix: {}", prefix);

      StorageProvider provider = storageProvider.get();

      try {
        // Upload thumbnail
        if (thumbnailPath.isPresent()) {
          Path thumbFile = thumbnailPath.get();
          String key = prefix + "/" + thumbFile.getFileName().toString();
          provider.store(thumbFile, key);
          storedKeys.add(key);
        }

        // Upload video variants
        for (Map.Entry<VideoResolution, Path> entry : transcodedVideos.entrySet()) {
          Path videoFile = entry.getValue();
          String key = prefix + "/" + videoFile.getFileName().toString();
          provider.store(videoFile, key);
          storedKeys.add(key);
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
        metadata, thumbnailPath, Map.copyOf(transcodedVideos), List.copyOf(storedKeys));
  }
}
