package io.transcodex.ffmpeg.executor;

import java.io.IOException;
import java.util.List;

/** Executes command-line processes (like ffmpeg or ffprobe) and returns their results. */
public interface FfmpegExecutor {

  /**
   * Executes an external process with the given arguments list.
   *
   * @param command the list of command line arguments (e.g., ["ffprobe", "-v", "error", ...]).
   * @return a CommandResult containing the exit code, stdout, and stderr output.
   * @throws IOException if process spawning or stream communication fails.
   * @throws InterruptedException if the execution thread is interrupted while waiting.
   */
  CommandResult execute(List<String> command) throws IOException, InterruptedException;
}
