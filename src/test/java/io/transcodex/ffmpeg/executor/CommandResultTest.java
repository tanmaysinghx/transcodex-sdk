package io.transcodex.ffmpeg.executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CommandResultTest {

  @Test
  void shouldCorrectlyReportSuccessForZeroExitCode() {
    CommandResult result = new CommandResult(0, "success-output", "");
    assertThat(result.success()).isTrue();
    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEqualTo("success-output");
    assertThat(result.stderr()).isEmpty();
  }

  @Test
  void shouldCorrectlyReportFailureForNonZeroExitCode() {
    CommandResult result = new CommandResult(1, "", "error-output");
    assertThat(result.success()).isFalse();
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).isEqualTo("error-output");
  }

  @Test
  void shouldThrowExceptionWhenStdoutOrStderrIsNull() {
    assertThatThrownBy(() -> new CommandResult(0, null, ""))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Stdout must not be null");

    assertThatThrownBy(() -> new CommandResult(0, "", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Stderr must not be null");
  }
}
