package io.transcodex.benchmarks;

import io.transcodex.api.metadata.MetadataExtractor;
import io.transcodex.api.video.*;
import io.transcodex.core.metadata.AudioStreamMetadata;
import io.transcodex.core.metadata.VideoMetadata;
import io.transcodex.core.metadata.VideoStreamMetadata;
import io.transcodex.core.video.VideoRequest;
import io.transcodex.core.video.VideoResolution;
import io.transcodex.core.video.VideoResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ParallelProcessingBenchmark {

  private Path sourceFile;
  private Path outputDir;

  private MetadataExtractor mockExtractor;
  private VideoTranscoder dummyTranscoder;
  private ThumbnailGenerator dummyGenerator;

  private ExecutorService virtualThreadExecutor;
  private ExecutorService fixedThreadPool;
  private ExecutorService forkJoinCommonPool;

  @Setup(Level.Trial)
  public void setUp() throws Exception {
    sourceFile = Files.createTempFile("benchmark-source", ".mp4");
    outputDir = Files.createTempDirectory("benchmark-output");

    mockExtractor =
        source ->
            new VideoMetadata(
                Duration.ofSeconds(10),
                1000L,
                100L,
                "mp4",
                new VideoStreamMetadata("h264", 1920, 1080, 30.0, "16:9", 80L),
                Optional.of(new AudioStreamMetadata("aac", 2, 48000, 20L)));

    // Simulate work by sleeping/writing a tiny file
    dummyTranscoder =
        (src, target, opts) -> {
          try {
            Thread.sleep(10); // simulate 10ms processing latency
            Files.createFile(target);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

    dummyGenerator =
        (src, target, opts) -> {
          try {
            Thread.sleep(5); // simulate 5ms extraction latency
            Files.createFile(target);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        };

    virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    fixedThreadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    forkJoinCommonPool = ForkJoinPool.commonPool();
  }

  @TearDown(Level.Trial)
  public void tearDown() throws IOException {
    Files.deleteIfExists(sourceFile);
    deleteDirectory(outputDir);
    virtualThreadExecutor.shutdown();
    fixedThreadPool.shutdown();
  }

  private void deleteDirectory(Path path) throws IOException {
    if (Files.exists(path)) {
      try (var walk = Files.walk(path)) {
        walk.sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(java.io.File::delete);
      }
    }
  }

  private VideoRequest buildRequest() {
    return VideoRequest.builder()
        .source(sourceFile)
        .outputDir(outputDir)
        .resolution(VideoResolution.P720)
        .resolution(VideoResolution.P1080)
        .generateThumbnail(true)
        .build();
  }

  @Benchmark
  public VideoResult benchmarkWithVirtualThreads() {
    DefaultVideoProcessor processor =
        new DefaultVideoProcessor(
            mockExtractor,
            dummyTranscoder,
            dummyGenerator,
            Optional.empty(),
            virtualThreadExecutor);
    return processor.process(buildRequest());
  }

  @Benchmark
  public VideoResult benchmarkWithFixedThreadPool() {
    DefaultVideoProcessor processor =
        new DefaultVideoProcessor(
            mockExtractor, dummyTranscoder, dummyGenerator, Optional.empty(), fixedThreadPool);
    return processor.process(buildRequest());
  }

  @Benchmark
  public VideoResult benchmarkWithForkJoinCommonPool() {
    DefaultVideoProcessor processor =
        new DefaultVideoProcessor(
            mockExtractor, dummyTranscoder, dummyGenerator, Optional.empty(), forkJoinCommonPool);
    return processor.process(buildRequest());
  }
}
