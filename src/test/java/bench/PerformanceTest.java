package bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

/**
 * Performance testing for GC benchmark
 * Tests performance characteristics and optimization scenarios
 */
@DisplayName("Performance Tests")
class PerformanceTest {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outputStream));
        // Force GC before each test
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Test High Allocation Performance")
    void testHighAllocationPerformance() {
        System.setProperty("alloc.bytes", "65536"); // 64KB allocations
        System.setProperty("churn.batch", "1000");
        System.setProperty("leak.every", "0"); // No leaks for pure performance test
        System.setProperty("run.seconds", "5");
        System.setProperty("report.ms", "2000");

        long startTime = System.currentTimeMillis();
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for performance test
        }

        long endTime = System.currentTimeMillis();
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform high allocation rate");
        
        long duration = endTime - startTime;
        double opsPerSecond = extractOperationsPerSecond(output);
        
        // Should complete within reasonable time
        assertTrue(duration < 15000, "Should complete within 15 seconds");
        assertTrue(opsPerSecond > 1000, "Should achieve reasonable ops/sec");
        
        System.out.printf("High allocation performance: %.2f ops/sec in %d ms%n", 
            opsPerSecond, duration);
    }

    @Test
    @DisplayName("Test Memory Churn Performance")
    void testMemoryChurnPerformance() {
        System.setProperty("alloc.bytes", "32768"); // 32KB allocations
        System.setProperty("churn.batch", "500");
        System.setProperty("leak.every", "0"); // No leaks
        System.setProperty("run.seconds", "4");
        System.setProperty("report.ms", "1500");

        long startTime = System.currentTimeMillis();
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for churn performance test
        }

        long endTime = System.currentTimeMillis();
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform memory churn");
        
        long duration = endTime - startTime;
        double opsPerSecond = extractOperationsPerSecond(output);
        long memoryGrowth = afterUsed - beforeUsed;
        
        // Should maintain good performance with memory churn
        assertTrue(duration < 12000, "Should complete within 12 seconds");
        assertTrue(opsPerSecond > 800, "Should maintain ops/sec with churn");
        assertTrue(memoryGrowth < 50 * 1024 * 1024, // 50MB tolerance
            "Memory growth should be controlled: " + (memoryGrowth / 1024 / 1024) + "MB");
        
        System.out.printf("Memory churn performance: %.2f ops/sec, %d MB growth in %d ms%n", 
            opsPerSecond, memoryGrowth / 1024 / 1024, duration);
    }

    @Test
    @DisplayName("Test Scalability Performance")
    void testScalabilityPerformance() {
        int[] batchSizes = {100, 500, 1000, 2000};
        
        for (int batchSize : batchSizes) {
            System.setProperty("alloc.bytes", "16384");
            System.setProperty("churn.batch", String.valueOf(batchSize));
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "2000");

            outputStream.reset();
            
            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for scalability test
            }
            
            long endTime = System.currentTimeMillis();
            String output = outputStream.toString();
            
            assertTrue(output.contains("ops="), 
                String.format("Should handle batch size %d", batchSize));
            
            long duration = endTime - startTime;
            double opsPerSecond = extractOperationsPerSecond(output);
            
            System.out.printf("Batch size %d: %.2f ops/sec in %d ms%n", 
                batchSize, opsPerSecond, duration);
            
            // Should complete within reasonable time for all batch sizes
            assertTrue(duration < 10000, 
                String.format("Should complete within 10 seconds for batch size %d", batchSize));
            assertTrue(opsPerSecond > 500, 
                String.format("Should maintain performance for batch size %d", batchSize));
        }
    }

    @Test
    @DisplayName("Test Allocation Size Performance Impact")
    void testAllocationSizePerformanceImpact() {
        int[] allocationSizes = {1024, 4096, 16384, 65536, 262144}; // 1KB to 256KB
        
        for (int allocSize : allocationSizes) {
            System.setProperty("alloc.bytes", String.valueOf(allocSize));
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "2000");

            outputStream.reset();
            
            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for allocation size test
            }
            
            long endTime = System.currentTimeMillis();
            String output = outputStream.toString();
            
            assertTrue(output.contains("ops="), 
                String.format("Should handle allocation size %d", allocSize));
            
            long duration = endTime - startTime;
            double opsPerSecond = extractOperationsPerSecond(output);
            
            System.out.printf("Allocation size %d: %.2f ops/sec in %d ms%n", 
                allocSize, opsPerSecond, duration);
            
            // Should complete within reasonable time for all allocation sizes
            assertTrue(duration < 15000, 
                String.format("Should complete within 15 seconds for allocation size %d", allocSize));
            assertTrue(opsPerSecond > 200, 
                String.format("Should maintain performance for allocation size %d", allocSize));
        }
    }

    @Test
    @DisplayName("Test Performance Under Memory Pressure")
    void testPerformanceUnderMemoryPressure() {
        System.setProperty("alloc.bytes", "131072"); // 128KB allocations
        System.setProperty("churn.batch", "300");
        System.setProperty("leak.every", "50"); // Some leaks to create pressure
        System.setProperty("run.seconds", "4");
        System.setProperty("report.ms", "1500");

        long startTime = System.currentTimeMillis();
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for pressure test
        }

        long endTime = System.currentTimeMillis();
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform under memory pressure");
        
        long duration = endTime - startTime;
        double opsPerSecond = extractOperationsPerSecond(output);
        long memoryGrowth = afterUsed - beforeUsed;
        
        // Should maintain reasonable performance under pressure
        assertTrue(duration < 15000, "Should complete within 15 seconds under pressure");
        assertTrue(opsPerSecond > 500, "Should maintain ops/sec under pressure");
        assertTrue(memoryGrowth > 10 * 1024 * 1024, // Should show memory pressure
            "Should show memory pressure: " + (memoryGrowth / 1024 / 1024) + "MB");
        
        System.out.printf("Performance under pressure: %.2f ops/sec, %d MB pressure in %d ms%n", 
            opsPerSecond, memoryGrowth / 1024 / 1024, duration);
    }

    @Test
    @DisplayName("Test CPU Utilization Performance")
    void testCPUUtilizationPerformance() {
        System.setProperty("alloc.bytes", "32768");
        System.setProperty("churn.batch", "400");
        System.setProperty("leak.every", "0");
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        long startTime = System.currentTimeMillis();
        double startCpuLoad = osBean.getProcessCpuLoad();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for CPU test
        }

        long endTime = System.currentTimeMillis();
        double endCpuLoad = osBean.getProcessCpuLoad();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should utilize CPU effectively");
        
        long duration = endTime - startTime;
        double opsPerSecond = extractOperationsPerSecond(output);
        double avgCpuLoad = (startCpuLoad + endCpuLoad) / 2;
        
        // Should utilize CPU effectively
        assertTrue(duration < 10000, "Should complete within 10 seconds");
        assertTrue(opsPerSecond > 1000, "Should achieve good ops/sec");
        assertTrue(avgCpuLoad > 0.1, "Should utilize CPU: " + (avgCpuLoad * 100) + "%");
        
        System.out.printf("CPU utilization: %.2f ops/sec, %.1f%% CPU in %d ms%n", 
            opsPerSecond, avgCpuLoad * 100, duration);
    }

    @Nested
    @DisplayName("Performance Optimization Tests")
    class PerformanceOptimizationTests {

        @Test
        @DisplayName("Test Optimized Allocation Pattern")
        void testOptimizedAllocationPattern() {
            System.setProperty("alloc.bytes", "16384"); // Optimal size
            System.setProperty("churn.batch", "250"); // Balanced batch
            System.setProperty("leak.every", "0"); // No leaks
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1500");

            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for optimization test
            }
            
            long endTime = System.currentTimeMillis();
            String output = outputStream.toString();
            
            assertTrue(output.contains("ops="), "Should use optimized allocation pattern");
            
            long duration = endTime - startTime;
            double opsPerSecond = extractOperationsPerSecond(output);
            
            // Should achieve optimal performance
            assertTrue(duration < 8000, "Should complete quickly with optimization");
            assertTrue(opsPerSecond > 1500, "Should achieve high ops/sec with optimization");
            
            System.out.printf("Optimized pattern: %.2f ops/sec in %d ms%n", opsPerSecond, duration);
        }

        @Test
        @DisplayName("Test Memory Pool Performance")
        void testMemoryPoolPerformance() {
            System.setProperty("alloc.bytes", "8192"); // Small allocations
            System.setProperty("churn.batch", "500"); // High batch
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for pool test
            }
            
            long endTime = System.currentTimeMillis();
            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should utilize memory pool effectively");
            
            long duration = endTime - startTime;
            double opsPerSecond = extractOperationsPerSecond(output);
            long memoryGrowth = afterUsed - beforeUsed;
            
            // Should show efficient memory pool usage
            assertTrue(duration < 10000, "Should complete efficiently");
            assertTrue(opsPerSecond > 2000, "Should achieve high ops/sec with small allocations");
            assertTrue(memoryGrowth < 20 * 1024 * 1024, // 20MB tolerance
                "Memory pool should be efficient: " + (memoryGrowth / 1024 / 1024) + "MB");
            
            System.out.printf("Memory pool: %.2f ops/sec, %d MB growth in %d ms%n", 
                opsPerSecond, memoryGrowth / 1024 / 1024, duration);
        }

        @Test
        @DisplayName("Test GC Performance Impact")
        void testGCPerformanceImpact() {
            System.setProperty("alloc.bytes", "65536"); // Large allocations
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for GC impact test
            }
            
            long endTime = System.currentTimeMillis();
            String output = outputStream.toString();
            
            assertTrue(output.contains("ops="), "Should handle GC impact effectively");
            
            long duration = endTime - startTime;
            double opsPerSecond = extractOperationsPerSecond(output);
            
            // Should maintain performance despite GC pressure
            assertTrue(duration < 12000, "Should complete despite GC pressure");
            assertTrue(opsPerSecond > 500, "Should maintain ops/sec despite GC");
            
            System.out.printf("GC impact: %.2f ops/sec in %d ms%n", opsPerSecond, duration);
        }
    }

    @Nested
    @DisplayName("Performance Regression Tests")
    class PerformanceRegressionTests {

        @Test
        @DisplayName("Test Performance Baseline")
        void testPerformanceBaseline() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "300");
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1500");

            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for baseline test
            }
            
            long endTime = System.currentTimeMillis();
            String output = outputStream.toString();
            
            assertTrue(output.contains("ops="), "Should establish performance baseline");
            
            long duration = endTime - startTime;
            double opsPerSecond = extractOperationsPerSecond(output);
            
            // Establish baseline performance metrics
            assertTrue(duration < 10000, "Baseline should complete within 10 seconds");
            assertTrue(opsPerSecond > 800, "Baseline should achieve reasonable ops/sec");
            
            System.out.printf("Performance baseline: %.2f ops/sec in %d ms%n", opsPerSecond, duration);
            
            // Store baseline for regression comparison (in real scenario)
            double baselineOpsPerSec = opsPerSecond;
            long baselineDuration = duration;
            
            // Run regression test
            outputStream.reset();
            startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for regression test
            }
            
            endTime = System.currentTimeMillis();
            output = outputStream.toString();
            
            duration = endTime - startTime;
            opsPerSecond = extractOperationsPerSecond(output);
            
            // Check for regression (within 20% tolerance)
            double performanceRatio = opsPerSecond / baselineOpsPerSec;
            double durationRatio = (double) duration / baselineDuration;
            
            assertTrue(performanceRatio >= 0.8, 
                String.format("Performance regression detected: %.2f%% degradation", 
                    (1 - performanceRatio) * 100));
            assertTrue(durationRatio <= 1.2, 
                String.format("Duration regression detected: %.2f%% increase", 
                    (durationRatio - 1) * 100));
            
            System.out.printf("Regression check: %.2f ops/sec (%.2fx baseline) in %d ms (%.2fx baseline)%n", 
                opsPerSecond, performanceRatio, duration, durationRatio);
        }

        @Test
        @DisplayName("Test Memory Performance Regression")
        void testMemoryPerformanceRegression() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "300");
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1500");

            // Baseline memory measurement
            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for memory baseline test
            }
            
            long endTime = System.currentTimeMillis();
            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long baselineMemoryGrowth = afterUsed - beforeUsed;

            // Regression test
            outputStream.reset();
            beforeUsed = memoryBean.getHeapMemoryUsage().getUsed();
            
            startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for memory regression test
            }
            
            endTime = System.currentTimeMillis();
            afterUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long memoryGrowth = afterUsed - beforeUsed;
            
            // Check for memory regression (within 50% tolerance)
            double memoryRatio = (double) memoryGrowth / baselineMemoryGrowth;
            
            assertTrue(memoryRatio <= 1.5, 
                String.format("Memory regression detected: %.2f%% increase", 
                    (memoryRatio - 1) * 100));
            
            System.out.printf("Memory regression: %d MB (%.2fx baseline)%n", 
                memoryGrowth / 1024 / 1024, memoryRatio);
        }
    }

    // Helper method to extract operations per second from output
    private double extractOperationsPerSecond(String output) {
        String[] lines = output.split("\n");
        double totalOps = 0;
        double totalTime = 0;
        
        for (String line : lines) {
            if (line.contains("uptime=") && line.contains("ops=")) {
                String[] parts = line.split("uptime=|ops=|bag=|leak=");
                if (parts.length >= 3) {
                    try {
                        double uptime = Double.parseDouble(parts[1].replace("fs", ""));
                        long ops = Long.parseLong(parts[2].trim());
                        totalOps += ops;
                        totalTime += uptime;
                    } catch (NumberFormatException e) {
                        // Ignore parsing errors
                    }
                }
            }
        }
        
        return totalTime > 0 ? totalOps / totalTime : 0;
    }
}
