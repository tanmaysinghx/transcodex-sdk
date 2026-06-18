package io.transcodex.playground;

import io.transcodex.api.video.DefaultVideoProcessor;
import io.transcodex.api.video.VideoProcessor;
import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.VideoAsset;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import io.transcodex.ffmpeg.video.FfmpegThumbnailGenerator;
import io.transcodex.ffmpeg.video.FfmpegVideoTranscoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@SpringBootApplication
@RestController
public class PlaygroundApp {

  private final VideoProcessor processor;
  private final Path baseStorageDir;
  private final int defaultThreads;

  public PlaygroundApp(
      @Value("${transcodex.storage.dir:#{null}}") String storageDirConfig,
      @Value("${transcodex.encoding.threads:0}") int defaultThreads)
      throws IOException {
    var executor = new ProcessBuilderExecutor();
    var metadataExtractor = new FfprobeMetadataExtractor(executor, new JacksonMetadataParser());
    var thumbnailGenerator = new FfmpegThumbnailGenerator(executor);
    var videoTranscoder = new FfmpegVideoTranscoder(executor);

    this.processor =
        new DefaultVideoProcessor(
            metadataExtractor,
            videoTranscoder,
            thumbnailGenerator,
            Optional.empty(),
            Executors.newVirtualThreadPerTaskExecutor());

    // Persistent storage: use configured dir or fallback to project-relative output
    if (storageDirConfig != null && !storageDirConfig.isBlank()) {
      this.baseStorageDir = Path.of(storageDirConfig);
    } else {
      this.baseStorageDir = Path.of("transcodex-playground", "output");
    }
    Files.createDirectories(this.baseStorageDir);
    this.defaultThreads = defaultThreads;
  }

  public static void main(String[] args) {
    SpringApplication.run(PlaygroundApp.class, args);
  }

  @PostMapping("/api/transcode")
  public ResponseEntity<?> transcode(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "resolutions", defaultValue = "P720") List<String> resolutions,
      @RequestParam(value = "generateThumbnail", defaultValue = "false")
          boolean generateThumbnail,
      @RequestParam(value = "thumbWidth", defaultValue = "320") int thumbWidth,
      @RequestParam(value = "thumbHeight", defaultValue = "180") int thumbHeight,
      @RequestParam(value = "thumbPosition", defaultValue = "0.5") double thumbPosition,
      @RequestParam(value = "generateHls", defaultValue = "false") boolean generateHls,
      @RequestParam(value = "encryptChunks", defaultValue = "false") boolean encryptChunks,
      @RequestParam(value = "encodingThreads", defaultValue = "0") int encodingThreads) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("Uploaded file is empty");
    }

    try {
      String sessionId = UUID.randomUUID().toString();
      Path sessionDir = baseStorageDir.resolve(sessionId);
      Files.createDirectories(sessionDir);

      String originalFilename = file.getOriginalFilename();
      String suffix =
          originalFilename != null && originalFilename.contains(".")
              ? originalFilename.substring(originalFilename.lastIndexOf("."))
              : ".mp4";
      Path inputPath = sessionDir.resolve("input" + suffix);
      file.transferTo(inputPath.toFile());

      int threads = encodingThreads > 0 ? encodingThreads : defaultThreads;

      VideoRequest.Builder requestBuilder =
          VideoRequest.builder()
              .source(inputPath)
              .outputDir(sessionDir)
              .generateHls(generateHls)
              .encryptChunks(encryptChunks)
              .encodingThreads(threads);

      for (String resStr : resolutions) {
        try {
          VideoResolution res = VideoResolution.valueOf(resStr.toUpperCase());
          requestBuilder.resolution(res);
        } catch (IllegalArgumentException ignored) {
        }
      }

      if (generateThumbnail) {
        requestBuilder
            .generateThumbnail(true)
            .thumbnailOptions(new ThumbnailOptions(thumbWidth, thumbHeight, thumbPosition, "jpg"));
      }

      VideoRequest request = requestBuilder.build();
      VideoResult result = processor.process(request);

      List<Map<String, Object>> transcodedFiles = new ArrayList<>();
      for (VideoAsset asset : result.transcodedAssets()) {
        String filename = asset.path().getFileName().toString();
        transcodedFiles.add(
            Map.of(
                "resolution",
                asset.resolution().name(),
                "sizeBytes",
                asset.sizeBytes(),
                "codec",
                asset.codec(),
                "downloadUrl",
                "/api/files/" + sessionId + "/" + filename));
      }

      String thumbnailDownloadUrl = null;
      if (result.thumbnail().isPresent()) {
        String thumbName = result.thumbnail().get().path().getFileName().toString();
        thumbnailDownloadUrl = "/api/files/" + sessionId + "/" + thumbName;
      }

      // Build response map (HashMap allows null values unlike Map.of)
      Map<String, Object> response = new HashMap<>();
      response.put("success", true);
      response.put("sessionId", sessionId);
      response.put(
          "metadata",
          Map.of(
              "format",
              result.metadata().format(),
              "width",
              result.metadata().videoStream().width(),
              "height",
              result.metadata().videoStream().height(),
              "durationSeconds",
              result.metadata().duration().toMillis() / 1000.0));
      response.put("transcodedAssets", transcodedFiles);
      response.put("thumbnailUrl", thumbnailDownloadUrl != null ? thumbnailDownloadUrl : "");
      response.put("encrypted", encryptChunks);

      if (result.masterPlaylist().isPresent()) {
        response.put(
            "masterPlaylistUrl",
            "/api/files/" + sessionId + "/" + result.masterPlaylist().get().getFileName());
      }

      if (result.encryptionKeyFile().isPresent()) {
        response.put("keyUrl", "/api/keys/" + sessionId);
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  /** Serves the AES-128 decryption key for a given session. */
  @GetMapping("/api/keys/{sessionId}")
  public ResponseEntity<Resource> serveKey(@PathVariable("sessionId") String sessionId) {
    Path keyFile = baseStorageDir.resolve(sessionId).resolve("encryption.key");
    if (!Files.exists(keyFile)) {
      return ResponseEntity.notFound().build();
    }

    Resource resource = new FileSystemResource(keyFile);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"encryption.key\"")
        .body(resource);
  }

  /** Serves any generated file (playlists, segments, thumbnails, videos). */
  @GetMapping("/api/files/{sessionId}/{filename:.+}")
  public ResponseEntity<Resource> serveFile(
      @PathVariable("sessionId") String sessionId,
      @PathVariable("filename") String filename) {
    Path filePath = baseStorageDir.resolve(sessionId).resolve(filename);
    if (!Files.exists(filePath)) {
      return ResponseEntity.notFound().build();
    }

    Resource resource = new FileSystemResource(filePath);
    String contentType = "application/octet-stream";
    if (filename.endsWith(".m3u8")) {
      contentType = "application/x-mpegURL";
    } else if (filename.endsWith(".ts")) {
      contentType = "video/MP2T";
    } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
      contentType = "image/jpeg";
    } else if (filename.endsWith(".mp4")) {
      contentType = "video/mp4";
    } else if (filename.endsWith(".key")) {
      contentType = "application/octet-stream";
    }

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
        .body(resource);
  }
}
