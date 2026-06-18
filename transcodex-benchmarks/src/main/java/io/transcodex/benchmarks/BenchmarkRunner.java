package io.transcodex.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchmarkRunner {
  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(MetadataExtractionBenchmark.class.getSimpleName())
            .include(ParallelProcessingBenchmark.class.getSimpleName())
            .forks(1)
            .build();

    new Runner(opt).run();
  }
}
