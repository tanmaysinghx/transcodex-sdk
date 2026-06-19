package io.transcodex.ffmpeg.executor;

import java.util.Objects;

/** Value object encapsulating the execution results of an external process. */
public record CommandResult(int exitCode, String stdout, String stderr) {
  public CommandResult {
    Objects.requireNonNull(stdout, "Stdout must not be null");
    Objects.requireNonNull(stderr, "Stderr must not be null");
  }

  public boolean success() {
    return exitCode == 0;
  }
}
