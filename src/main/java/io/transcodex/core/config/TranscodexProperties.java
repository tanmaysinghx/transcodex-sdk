package io.transcodex.core.config;

import io.transcodex.core.video.VideoResolution;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds default configuration properties for SDK operations. Can load properties from a classpath
 * {@code transcodex.properties} file, or be configured programmatically/via Spring Boot binding.
 */
public class TranscodexProperties {

  private static final Logger log = LoggerFactory.getLogger(TranscodexProperties.class);

  private List<VideoResolution> defaultResolutions = List.of(VideoResolution.P360, VideoResolution.P720);
  private boolean defaultEncryptChunks = false;
  private int defaultEncodingThreads = 4;
  private boolean defaultGenerateHls = true;
  private boolean defaultGenerateThumbnail = true;
  private int defaultThumbnailWidth = 320;
  private int defaultThumbnailHeight = 180;
  private String defaultThumbnailFormat = "jpg";
  private double defaultThumbnailPositionSeconds = 0.5;

  public TranscodexProperties() {
    loadFromClasspath();
  }

  private void loadFromClasspath() {
    try (InputStream in = TranscodexProperties.class.getResourceAsStream("/transcodex.properties")) {
      if (in == null) {
        log.debug("No transcodex.properties found on classpath, using default SDK settings.");
        return;
      }
      Properties props = new Properties();
      props.load(in);

      String resProp = props.getProperty("transcodex.default.resolutions");
      if (resProp != null && !resProp.isBlank()) {
        defaultResolutions = Arrays.stream(resProp.split(","))
            .map(String::trim)
            .map(String::toUpperCase)
            .map(val -> {
              switch (val) {
                case "360P": return VideoResolution.P360;
                case "480P": return VideoResolution.P480;
                case "720P": return VideoResolution.P720;
                case "1080P": return VideoResolution.P1080;
                case "2160P":
                case "4K": return VideoResolution.P2160;
                case "4320P":
                case "8K": return VideoResolution.P4320;
                default:
                  log.warn("Unknown resolution '{}', using 720p", val);
                  return VideoResolution.P720;
              }
            })
            .toList();
      }

      defaultEncryptChunks = Boolean.parseBoolean(props.getProperty("transcodex.default.encrypt-chunks", String.valueOf(defaultEncryptChunks)));
      defaultEncodingThreads = Integer.parseInt(props.getProperty("transcodex.default.encoding-threads", String.valueOf(defaultEncodingThreads)));
      defaultGenerateHls = Boolean.parseBoolean(props.getProperty("transcodex.default.generate-hls", String.valueOf(defaultGenerateHls)));
      defaultGenerateThumbnail = Boolean.parseBoolean(props.getProperty("transcodex.default.generate-thumbnail", String.valueOf(defaultGenerateThumbnail)));
      defaultThumbnailWidth = Integer.parseInt(props.getProperty("transcodex.default.thumbnail.width", String.valueOf(defaultThumbnailWidth)));
      defaultThumbnailHeight = Integer.parseInt(props.getProperty("transcodex.default.thumbnail.height", String.valueOf(defaultThumbnailHeight)));
      defaultThumbnailFormat = props.getProperty("transcodex.default.thumbnail.format", defaultThumbnailFormat);
      defaultThumbnailPositionSeconds = Double.parseDouble(props.getProperty("transcodex.default.thumbnail.position-seconds", String.valueOf(defaultThumbnailPositionSeconds)));

      log.info("Successfully loaded configuration from transcodex.properties");
    } catch (Exception e) {
      log.error("Failed to load transcodex.properties", e);
    }
  }

  public List<VideoResolution> getDefaultResolutions() {
    return defaultResolutions;
  }

  public void setDefaultResolutions(List<VideoResolution> defaultResolutions) {
    this.defaultResolutions = defaultResolutions;
  }

  public boolean isDefaultEncryptChunks() {
    return defaultEncryptChunks;
  }

  public void setDefaultEncryptChunks(boolean defaultEncryptChunks) {
    this.defaultEncryptChunks = defaultEncryptChunks;
  }

  public int getDefaultEncodingThreads() {
    return defaultEncodingThreads;
  }

  public void setDefaultEncodingThreads(int defaultEncodingThreads) {
    this.defaultEncodingThreads = defaultEncodingThreads;
  }

  public boolean isDefaultGenerateHls() {
    return defaultGenerateHls;
  }

  public void setDefaultGenerateHls(boolean defaultGenerateHls) {
    this.defaultGenerateHls = defaultGenerateHls;
  }

  public boolean isDefaultGenerateThumbnail() {
    return defaultGenerateThumbnail;
  }

  public void setDefaultGenerateThumbnail(boolean defaultGenerateThumbnail) {
    this.defaultGenerateThumbnail = defaultGenerateThumbnail;
  }

  public int getDefaultThumbnailWidth() {
    return defaultThumbnailWidth;
  }

  public void setDefaultThumbnailWidth(int defaultThumbnailWidth) {
    this.defaultThumbnailWidth = defaultThumbnailWidth;
  }

  public int getDefaultThumbnailHeight() {
    return defaultThumbnailHeight;
  }

  public void setDefaultThumbnailHeight(int defaultThumbnailHeight) {
    this.defaultThumbnailHeight = defaultThumbnailHeight;
  }

  public String getDefaultThumbnailFormat() {
    return defaultThumbnailFormat;
  }

  public void setDefaultThumbnailFormat(String defaultThumbnailFormat) {
    this.defaultThumbnailFormat = defaultThumbnailFormat;
  }

  public double getDefaultThumbnailPositionSeconds() {
    return defaultThumbnailPositionSeconds;
  }

  public void setDefaultThumbnailPositionSeconds(double defaultThumbnailPositionSeconds) {
    this.defaultThumbnailPositionSeconds = defaultThumbnailPositionSeconds;
  }
}
