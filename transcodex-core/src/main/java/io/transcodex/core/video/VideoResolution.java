package io.transcodex.core.video;

/** Standard video resolutions with their typical properties. */
public enum VideoResolution {
  P360(640, 360, "360p", 800_000L),
  P480(854, 480, "480p", 1_400_000L),
  P720(1280, 720, "720p", 2_800_000L),
  P1080(1920, 1080, "1080p", 5_000_000L),
  P2160(3840, 2160, "4K (2160p)", 20_000_000L);

  private final int width;
  private final int height;
  private final String label;
  private final long defaultBitrateBps;

  VideoResolution(int width, int height, String label, long defaultBitrateBps) {
    this.width = width;
    this.height = height;
    this.label = label;
    this.defaultBitrateBps = defaultBitrateBps;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public String label() {
    return label;
  }

  public long defaultBitrateBps() {
    return defaultBitrateBps;
  }
}
