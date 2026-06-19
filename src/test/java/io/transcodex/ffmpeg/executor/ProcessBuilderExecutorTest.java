package io.transcodex.ffmpeg.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessBuilderExecutorTest {

  private ProcessBuilderExecutor executor;

  @BeforeEach
  void setUp() {
    executor = new ProcessBuilderExecutor();
  }

  private List<String> getEchoCommand(String message) {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      return List.of("cmd.exe", "/c", "echo " + message);
    } else {
      return List.of("sh", "-c", "echo " + message);
    }
  }

  private List<String> getFailureCommand() {
    String os = System.getProperty("os.name").toLowerCase();
    if (os.contains("win")) {
      return List.of("cmd.exe", "/c", "dir non-existent-directory-12345");
    } else {
      return List.of("sh", "-c", "ls non-existent-directory-12345");
    }
  }

  @Test
  void shouldSuccessfullyExecuteCommandAndCaptureOutput() throws Exception {
    List<String> command = getEchoCommand("hello-transcodex");
    CommandResult result = executor.execute(command);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.success()).isTrue();
    assertThat(result.stdout()).contains("hello-transcodex");
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void shouldCaptureStderrForFailedCommand() throws Exception {
    List<String> command = getFailureCommand();
    CommandResult result = executor.execute(command);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.success()).isFalse();
  }

  @Test
  void shouldThrowExceptionForInvalidExecutable() {
    List<String> command = List.of("invalid-executable-that-does-not-exist");
    assertThatThrownBy(() -> executor.execute(command)).isInstanceOf(IOException.class);
  }

  @Test
  void shouldThrowExceptionForNullOrEmptyCommand() {
    assertThatThrownBy(() -> executor.execute(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> executor.execute(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowTimeoutExceptionForLongRunningCommand() {
    String os = System.getProperty("os.name").toLowerCase();
    List<String> command;
    if (os.contains("win")) {
      command = List.of("ping", "-n", "10", "127.0.0.1"); // takes ~9 seconds
    } else {
      command = List.of("sleep", "9");
    }

    assertThatThrownBy(() -> executor.execute(command, 1))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("timed out after 1 seconds");
  }
}
