package io.transcodex.benchmarks;

import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.ffmpeg.executor.ProcessBuilderExecutor;
import io.transcodex.ffmpeg.metadata.FfprobeMetadataExtractor;
import io.transcodex.ffmpeg.metadata.JacksonMetadataParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class MetadataExtractionBenchmark {

  private Path testVideo;
  private FfprobeMetadataExtractor extractor;

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    testVideo = Files.createTempFile("transcodex-benchmark-sample", ".mp4");
    // Generate a tiny 1-second video
    new ProcessBuilderExecutor()
        .execute(
            List.of(
                "ffmpeg",
                "-y",
                "-f",
                "lavfi",
                "-i",
                "color=c=black:s=640x360:d=1",
                "-c:v",
                "libx264",
                "-pix_fmt",
                "yuv420p",
                testVideo.toAbsolutePath().toString()));

    extractor =
        new FfprobeMetadataExtractor(new ProcessBuilderExecutor(), new JacksonMetadataParser());
  }

  @TearDown(Level.Trial)
  public void tearDown() throws IOException {
    Files.deleteIfExists(testVideo);
  }

  @Benchmark
  public VideoMetadata benchmarkMetadataExtraction() {
    return extractor.extract(testVideo);
  }
}
