package io.transcodex.ffmpeg.executor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Concrete implementation of FfmpegExecutor using Java's ProcessBuilder. Leverages virtual threads
 * to read standard output and error streams without blocking OS threads.
 */
public class ProcessBuilderExecutor implements FfmpegExecutor {

  @Override
  public CommandResult execute(List<String> command) throws IOException, InterruptedException {
    return execute(command, 1800); // 30 minutes default
  }

  @Override
  public CommandResult execute(List<String> command, long timeoutSeconds)
      throws IOException, InterruptedException {
    if (command == null || command.isEmpty()) {
      throw new IllegalArgumentException("Command list must not be null or empty");
    }

    Process process = new ProcessBuilder(command).start();

    StringBuilder stdoutBuilder = new StringBuilder();
    StringBuilder stderrBuilder = new StringBuilder();

    try (InputStream stdoutStream = process.getInputStream();
        InputStream stderrStream = process.getErrorStream()) {

      // Start virtual threads to drain stdout and stderr concurrently
      Thread stdoutThread = Thread.ofVirtual().start(() -> readStream(stdoutStream, stdoutBuilder));
      Thread stderrThread = Thread.ofVirtual().start(() -> readStream(stderrStream, stderrBuilder));

      boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

      if (!completed) {
        process.destroyForcibly();
        stdoutThread.interrupt();
        stderrThread.interrupt();
        throw new IOException("Process execution timed out after " + timeoutSeconds + " seconds");
      }

      stdoutThread.join();
      stderrThread.join();

      return new CommandResult(
          process.exitValue(), stdoutBuilder.toString().trim(), stderrBuilder.toString().trim());

    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw e;
    }
  }

  private void readStream(InputStream stream, StringBuilder target) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        target.append(buffer, 0, read);
      }
    } catch (IOException e) {
      // Ignore or log error
    }
  }
}
