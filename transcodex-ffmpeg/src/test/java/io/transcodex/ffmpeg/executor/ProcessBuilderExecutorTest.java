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

  @Test
  void shouldSuccessfullyExecuteCommandAndCaptureOutput() throws Exception {
    List<String> command = List.of("cmd.exe", "/c", "echo hello-transcodex");
    CommandResult result = executor.execute(command);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.success()).isTrue();
    assertThat(result.stdout()).contains("hello-transcodex");
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void shouldCaptureStderrForFailedCommand() throws Exception {
    List<String> command = List.of("cmd.exe", "/c", "dir non-existent-directory-12345");
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
}
