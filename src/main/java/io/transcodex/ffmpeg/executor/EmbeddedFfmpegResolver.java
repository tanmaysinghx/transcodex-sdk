package io.transcodex.ffmpeg.executor;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the path to the ffmpeg and ffprobe binaries. On Windows, if the binaries are bundled in
 * the resources, they are extracted to a temporary folder and run from there. On other operating
 * systems, it falls back to the system-installed ffmpeg and ffprobe.
 */
public final class EmbeddedFfmpegResolver {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedFfmpegResolver.class);
  private static final String OS = System.getProperty("os.name").toLowerCase();

  private static String ffmpegPath = "ffmpeg";
  private static String ffprobePath = "ffprobe";
  private static boolean resolved = false;

  private EmbeddedFfmpegResolver() {}

  public static synchronized void resolve() {
    if (resolved) {
      return;
    }

    if (OS.contains("win")) {
      try {
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "transcodex-bin");
        Files.createDirectories(tempDir);

        Path ffmpegFile = tempDir.resolve("ffmpeg.exe");
        Path ffprobeFile = tempDir.resolve("ffprobe.exe");

        boolean ffmpegExtracted = extractResource("/bin/windows/ffmpeg.exe", ffmpegFile);
        boolean ffprobeExtracted = extractResource("/bin/windows/ffprobe.exe", ffprobeFile);

        if (ffmpegExtracted && ffprobeExtracted) {
          ffmpegPath = ffmpegFile.toAbsolutePath().toString();
          ffprobePath = ffprobeFile.toAbsolutePath().toString();
          log.info("Successfully resolved embedded Windows binaries: {}, {}", ffmpegPath, ffprobePath);
        } else {
          log.warn("Embedded FFmpeg/FFprobe binaries not found in classpath resources. Falling back to system PATH.");
        }
      } catch (Exception e) {
        log.error("Failed to resolve embedded FFmpeg binaries. Falling back to system PATH.", e);
      }
    } else {
      log.info("Non-Windows OS detected ({}). Falling back to system PATH for ffmpeg and ffprobe.", OS);
    }
    resolved = true;
  }

  private static boolean extractResource(String resourcePath, Path targetFile) {
    try (InputStream in = EmbeddedFfmpegResolver.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        return false;
      }

      if (Files.exists(targetFile) && Files.size(targetFile) > 0) {
        // Already extracted, skip to save startup time
        return true;
      }

      log.info("Extracting embedded binary {} to {}...", resourcePath, targetFile);
      try (OutputStream out = Files.newOutputStream(targetFile)) {
        byte[] buffer = new byte[65536];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          out.write(buffer, 0, bytesRead);
        }
      }

      try {
        targetFile.toFile().setExecutable(true);
      } catch (Exception e) {
        log.warn("Could not set executable permission on extracted binary: {}", targetFile, e);
      }
      return true;
    } catch (Exception e) {
      log.error("Error extracting resource: " + resourcePath, e);
      return false;
    }
  }

  public static String getFfmpegPath() {
    resolve();
    return ffmpegPath;
  }

  public static String getFfprobePath() {
    resolve();
    return ffprobePath;
  }
}
