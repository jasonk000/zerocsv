package benchmark;

import csv.CsvTokenizer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.profile.LinuxPerfAsmProfiler;

@BenchmarkMode(Mode.AverageTime)
@Fork(1)
@Warmup(iterations = 3, time = 5000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 7000, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class BenchmarkParserAndMapperInMemory {
    
    private static final String TEST_FILE = "src/test/resources/cities-unix.txt"; 
    
    @State(Scope.Benchmark)
    public static class BenchmarkState {
        
        byte[] content = loadFile(prepareFile(100, TEST_FILE));
        
        private File prepareFile(int sizeMultiplier, String testFile) {
            try {
                byte[] content= FileUtils.readFileToByteArray(new File(testFile));
                File result = File.createTempFile("csv", "large");
                for (int i = 0; i < sizeMultiplier; i++) {
                    FileUtils.writeByteArrayToFile(result, content, true);
                }
                return result;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private byte[] loadFile(File file) {
            try {
                return FileUtils.readFileToByteArray(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
 
    @Benchmark
    public void benchmarkByteArrayParser(BenchmarkState state, Blackhole bh) throws IOException {
        CsvTokenizer st = new CsvTokenizer(state.content, 7);
        while(st.parseLine()) {
            City c = new City(st);
            // System.out.println(c);
            bh.consume(c);
        }
    }
 
    @Benchmark
    public void benchmarkZeroGarbageParser(BenchmarkState state, Blackhole bh) throws IOException {
        CsvTokenizer st = new CsvTokenizer(state.content, 7);
        while(st.parseLine()) {
            CityZ c = new CityZ(st);
            bh.consume(c);
        }
    }

    
    @Benchmark
    public void benchmarkInputStreamParser(BenchmarkState state, Blackhole bh) throws IOException {
        CsvTokenizer st = new CsvTokenizer(new ByteArrayInputStream(state.content), 65536, 7);
        while(st.parseLine()) {
            City c = new City(st); 
            bh.consume(c);
        }
    }
       
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(".*" + BenchmarkParserAndMapperInMemory.class.getSimpleName()+".*")
            // .addProfiler(LinuxPerfAsmProfiler.class)
            .addProfiler(StackProfiler.class)
            .build();
        new Runner(opt).run();
    }

}
