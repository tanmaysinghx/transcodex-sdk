package io.transcodex.cli;

import io.transcodex.api.video.VideoProcessor;
import io.transcodex.api.video.VideoProcessorFactory;
import io.transcodex.core.config.TranscodexProperties;
import io.transcodex.core.video.ThumbnailOptions;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line runner for the TranscodeX SDK. Allows users to transcode video files directly from
 * the terminal using the SDK's high-performance features.
 */
public class TranscodeXCli {

  public static void main(String[] args) {
    if (args.length == 0 || hasArg(args, "-h", "--help")) {
      printHelp();
      return;
    }

    try {
      TranscodexProperties props = new TranscodexProperties();
      VideoRequest request = parseArgs(args, props);

      if (request == null) {
        // Help or no action required
        return;
      }

      System.out.println("Initializing TranscodeX Processor...");
      VideoProcessor processor = VideoProcessorFactory.createDefault();

      System.out.println("Processing video: " + request.source().toAbsolutePath());
      System.out.println("Output directory: " + request.outputDir().toAbsolutePath());

      VideoResult result = processor.process(request);

      System.out.println("\n--- Processing Completed Successfully ---");
      if (result.metadata() != null) {
        System.out.println("Duration: " + result.metadata().duration().toSeconds() + " seconds");
        System.out.println("Format: " + result.metadata().format());
      }
      result
          .masterPlaylist()
          .ifPresent(p -> System.out.println("Master Playlist generated: " + p.toAbsolutePath()));
      result
          .thumbnail()
          .ifPresent(t -> System.out.println("Thumbnail generated: " + t.path().toAbsolutePath()));
      System.exit(0);
    } catch (IllegalArgumentException e) {
      System.err.println("Error: " + e.getMessage());
      printHelp();
      System.exit(1);
    } catch (Exception e) {
      System.err.println("Error executing transcoding pipeline: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Parses command-line arguments and returns a configured {@link VideoRequest}.
   *
   * @param args the command-line arguments.
   * @param props default Transcodex properties.
   * @return the built VideoRequest, or null if the user asked for help.
   * @throws IllegalArgumentException if required parameters are missing or invalid.
   */
  public static VideoRequest parseArgs(String[] args, TranscodexProperties props) {
    if (hasArg(args, "-h", "--help")) {
      return null;
    }

    Path input = getPathArg(args, "-i", "--input");
    Path output = getPathArg(args, "-o", "--output");

    if (input == null) {
      throw new IllegalArgumentException("Missing required argument -i/--input");
    }
    if (output == null) {
      throw new IllegalArgumentException("Missing required argument -o/--output");
    }

    if (!Files.exists(input)) {
      throw new IllegalArgumentException("Input file does not exist: " + input.toAbsolutePath());
    }

    VideoRequest.Builder builder = VideoRequest.builder(props);
    builder.source(input);
    builder.outputDir(output);

    // Parse resolutions if explicitly supplied
    String resArg = getStringArg(args, "-r", "--resolutions");
    if (resArg != null) {
      List<VideoResolution> resolutions = new ArrayList<>();
      for (String res : resArg.split(",")) {
        res = res.trim().toLowerCase();
        switch (res) {
          case "360p" -> resolutions.add(VideoResolution.P360);
          case "480p" -> resolutions.add(VideoResolution.P480);
          case "720p" -> resolutions.add(VideoResolution.P720);
          case "1080p" -> resolutions.add(VideoResolution.P1080);
          case "2160p", "4k" -> resolutions.add(VideoResolution.P2160);
          case "4320p", "8k" -> resolutions.add(VideoResolution.P4320);
          default ->
              System.err.println(
                  "Warning: Unknown resolution '"
                      + res
                      + "'. Supported: 360p, 480p, 720p, 1080p, 2160p (4k), 4320p (8k)");
        }
      }
      if (!resolutions.isEmpty()) {
        // Re-create builder to reset defaults
        builder = VideoRequest.builder();
        builder.source(input);
        builder.outputDir(output);
        builder.resolutions(resolutions);
        // Re-apply properties defaults that were not overridden by options
        builder.maxConcurrentTranscodes(props.getDefaultMaxConcurrentTranscodes());
        builder.processTimeoutSeconds(props.getDefaultProcessTimeoutSeconds());
      }
    }

    // Parse HLS option
    boolean noHls = hasArg(args, "--no-hls");
    builder.generateHls(!noHls);

    // Parse encryption
    boolean encrypt = hasArg(args, "-e", "--encrypt");
    if (encrypt) {
      builder.encryptChunks(true);
      builder.generateHls(true); // force HLS if encrypting
    } else {
      builder.encryptChunks(props.isDefaultEncryptChunks());
    }

    // Parse threads
    String threadsArg = getStringArg(args, "-t", "--threads");
    if (threadsArg != null) {
      try {
        builder.encodingThreads(Integer.parseInt(threadsArg));
      } catch (NumberFormatException e) {
        System.err.println(
            "Warning: Invalid number format for --threads, using default: "
                + props.getDefaultEncodingThreads());
        builder.encodingThreads(props.getDefaultEncodingThreads());
      }
    } else {
      builder.encodingThreads(props.getDefaultEncodingThreads());
    }

    // Parse thumbnail overrides
    boolean noThumbnail = hasArg(args, "--no-thumbnail");
    if (noThumbnail) {
      builder.generateThumbnail(false);
    } else {
      builder.generateThumbnail(true);
      int thumbWidth = getIntArg(args, "--thumbnail-width", props.getDefaultThumbnailWidth());
      int thumbHeight = getIntArg(args, "--thumbnail-height", props.getDefaultThumbnailHeight());
      double thumbPos =
          getDoubleArg(args, "--thumbnail-pos", props.getDefaultThumbnailPositionSeconds());
      String thumbFormat = getStringArg(args, "--thumbnail-format");
      if (thumbFormat == null) {
        thumbFormat = props.getDefaultThumbnailFormat();
      }
      builder.thumbnailOptions(
          new ThumbnailOptions(thumbWidth, thumbHeight, thumbPos, thumbFormat));
    }

    return builder.build();
  }

  private static boolean hasArg(String[] args, String... flags) {
    for (String arg : args) {
      for (String flag : flags) {
        if (arg.equalsIgnoreCase(flag)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String getStringArg(String[] args, String... flags) {
    for (int i = 0; i < args.length - 1; i++) {
      for (String flag : flags) {
        if (args[i].equalsIgnoreCase(flag)) {
          return args[i + 1];
        }
      }
    }
    return null;
  }

  private static Path getPathArg(String[] args, String... flags) {
    String val = getStringArg(args, flags);
    return val != null ? Path.of(val) : null;
  }

  private static int getIntArg(String[] args, String flag, int defaultValue) {
    String val = getStringArg(args, flag);
    if (val != null) {
      try {
        return Integer.parseInt(val);
      } catch (NumberFormatException e) {
        System.err.println(
            "Warning: Invalid number format for " + flag + ", using default: " + defaultValue);
      }
    }
    return defaultValue;
  }

  private static double getDoubleArg(String[] args, String flag, double defaultValue) {
    String val = getStringArg(args, flag);
    if (val != null) {
      try {
        return Double.parseDouble(val);
      } catch (NumberFormatException e) {
        System.err.println(
            "Warning: Invalid double format for " + flag + ", using default: " + defaultValue);
      }
    }
    return defaultValue;
  }

  private static void printHelp() {
    System.out.println(
        """
      TranscodeX CLI - High-performance, Java 25 FFmpeg Media Processing Tool

      Usage:
        java -jar transcodex-sdk-0.1.0-SNAPSHOT-all.jar -i <input_path> -o <output_dir> [options]

      Required Arguments:
        -i, --input <path>        Path to the input video file.
        -o, --output <dir>        Path to the directory where transcoded segments, playlists and thumbnails will be saved.

      Optional Arguments:
        -r, --resolutions <list>  Comma-separated list of resolutions to generate.
                                  Supported: 360p, 480p, 720p, 1080p, 2160p (4k), 4320p (8k).
                                  Defaults to: 360p,720p (as configured in transcodex.properties).
        -e, --encrypt             Enable AES-128 HLS chunk encryption (auto-enables HLS playlists).
        -t, --threads <num>       Number of CPU threads to allocate for transcoding (default: 4).
        --no-thumbnail            Disable thumbnail generation (thumbnail is generated by default).
        --no-hls                  Disable HLS adaptive stream playlist generation.
        --thumbnail-width <num>   Custom width for generated thumbnail (default: 320).
        --thumbnail-height <num>  Custom height for generated thumbnail (default: 180).
        --thumbnail-pos <sec>     Timestamp offset (in seconds) to extract thumbnail keyframe (default: 0.5).
        --thumbnail-format <fmt>  Thumbnail format: jpg or png (default: jpg).
        -h, --help                Show this help screen.
      """);
  }
}
