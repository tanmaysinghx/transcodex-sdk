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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@SpringBootApplication
@RestController
public class PlaygroundApp {

  private final VideoProcessor processor;
  private final Map<String, Path> fileCache = new ConcurrentHashMap<>();
  private final Path baseStorageDir;

  public PlaygroundApp() throws IOException {
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

    this.baseStorageDir = Files.createTempDirectory("transcodex-web-storage");
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
      @RequestParam(value = "thumbPosition", defaultValue = "0.5") double thumbPosition) {

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

      VideoRequest.Builder requestBuilder =
          VideoRequest.builder().source(inputPath).outputDir(sessionDir);

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
        String fileId = UUID.randomUUID().toString();
        fileCache.put(fileId, asset.path());
        transcodedFiles.add(
            Map.of(
                "resolution",
                asset.resolution().name(),
                "sizeBytes",
                asset.sizeBytes(),
                "codec",
                asset.codec(),
                "downloadUrl",
                "/api/download?id="
                    + fileId
                    + "&name="
                    + asset.path().getFileName().toString()));
      }

      String thumbnailDownloadUrl = null;
      if (result.thumbnail().isPresent()) {
        String fileId = UUID.randomUUID().toString();
        fileCache.put(fileId, result.thumbnail().get().path());
        thumbnailDownloadUrl =
            "/api/download?id="
                + fileId
                + "&name="
                + result.thumbnail().get().path().getFileName().toString();
      }

      Map<String, Object> response =
          Map.of(
              "success",
              true,
              "metadata",
              Map.of(
                  "format",
                  result.metadata().format(),
                  "width",
                  result.metadata().videoStream().width(),
                  "height",
                  result.metadata().videoStream().height(),
                  "durationSeconds",
                  result.metadata().duration().toMillis() / 1000.0),
              "transcodedAssets",
              transcodedFiles,
              "thumbnailUrl",
              thumbnailDownloadUrl != null ? thumbnailDownloadUrl : "");

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("success", false, "error", e.getMessage()));
    }
  }

  @GetMapping("/api/download")
  public ResponseEntity<Resource> download(
      @RequestParam("id") String id, @RequestParam("name") String name) {
    Path filePath = fileCache.get(id);
    if (filePath == null || !Files.exists(filePath)) {
      return ResponseEntity.notFound().build();
    }

    Resource resource = new FileSystemResource(filePath);
    String contentType = "application/octet-stream";
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      contentType = "image/jpeg";
    } else if (name.endsWith(".mp4")) {
      contentType = "video/mp4";
    }

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .body(resource);
  }
}
