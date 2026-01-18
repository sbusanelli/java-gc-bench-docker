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
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for GC benchmark functionality
 * Tests garbage collection behavior and memory management
 */
@DisplayName("GC Benchmark Integration Tests")
class GCBenchmarkTest {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

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
    @DisplayName("Test GC Behavior Under Memory Pressure")
    void testGCBehaviorUnderMemoryPressure() {
        System.setProperty("alloc.bytes", "1048576"); // 1MB allocations
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "50");
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for integration test
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations under memory pressure");
        assertTrue(output.contains("leak="), "Should track memory leaks");
        
        // Memory usage should increase due to leaks
        assertTrue(afterUsed > beforeUsed, "Memory usage should increase with leaks");
        
        System.out.printf("Memory before: %d MB, after: %d MB%n", 
            beforeUsed / 1024 / 1024, afterUsed / 1024 / 1024);
    }

    @Test
    @DisplayName("Test Memory Churn Without Leaks")
    void testMemoryChurnWithoutLeaks() {
        System.setProperty("alloc.bytes", "524288"); // 512KB allocations
        System.setProperty("churn.batch", "200");
        System.setProperty("leak.every", "0"); // No leaks
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for integration test
        }

        // Force GC to see if memory is reclaimed
        System.gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform memory churn");
        assertTrue(output.contains("leak=0"), "Should not leak memory");
        
        // Memory usage should be relatively stable without leaks
        long memoryGrowth = Math.abs(afterUsed - beforeUsed);
        assertTrue(memoryGrowth < 50 * 1024 * 1024, // 50MB tolerance
            "Memory growth should be minimal without leaks: " + (memoryGrowth / 1024 / 1024) + "MB");
        
        System.out.printf("Memory growth without leaks: %d MB%n", memoryGrowth / 1024 / 1024);
    }

    @Test
    @DisplayName("Test High Allocation Rate GC Impact")
    void testHighAllocationRateGCImpact() {
        System.setProperty("alloc.bytes", "65536"); // 64KB allocations
        System.setProperty("churn.batch", "1000");
        System.setProperty("leak.every", "100");
        System.setProperty("run.seconds", "5");
        System.setProperty("report.ms", "2000");

        long startTime = System.currentTimeMillis();
        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for integration test
        }

        long endTime = System.currentTimeMillis();
        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should handle high allocation rate");
        
        // Should complete within reasonable time
        long duration = endTime - startTime;
        assertTrue(duration < 15000, "Should complete within 15 seconds");
        
        // Should show multiple reports indicating sustained activity
        long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
        assertTrue(reportCount >= 2, "Should generate multiple reports during high allocation");
        
        System.out.printf("High allocation test completed in %d ms with %d reports%n", 
            duration, reportCount);
    }

    @Test
    @DisplayName("Test Memory Leak Detection")
    void testMemoryLeakDetection() {
        System.setProperty("alloc.bytes", "32768"); // 32KB allocations
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "10"); // High leak frequency
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for integration test
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        long memoryLeaked = afterUsed - beforeUsed;

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations");
        assertTrue(output.contains("leak="), "Should detect memory leaks");
        
        // Should show significant memory growth due to leaks
        assertTrue(memoryLeaked > 10 * 1024 * 1024, // 10MB minimum leak
            "Should detect significant memory leaks: " + (memoryLeaked / 1024 / 1024) + "MB");
        
        System.out.printf("Memory leaked: %d MB%n", memoryLeaked / 1024 / 1024);
    }

    @Test
    @DisplayName("Test GC Pause Simulation")
    void testGCPauseSimulation() {
        System.setProperty("alloc.bytes", "1048576"); // 1MB allocations
        System.setProperty("churn.batch", "500");
        System.setProperty("leak.every", "250");
        System.setProperty("run.seconds", "4");
        System.setProperty("report.ms", "1500");

        long startTime = System.currentTimeMillis();
        long totalPauseTime = 0;
        int pauseCount = 0;

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for integration test
        }

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should complete despite GC pauses");
        
        // Should complete within reasonable time despite GC pressure
        assertTrue(totalDuration < 20000, "Should complete within 20 seconds");
        
        // Should show consistent performance reporting
        long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
        assertTrue(reportCount >= 2, "Should generate reports during GC activity");
        
        System.out.printf("GC pause simulation completed in %d ms with %d reports%n", 
            totalDuration, reportCount);
    }

    @Nested
    @DisplayName("Memory Management Tests")
    class MemoryManagementTests {

        @Test
        @DisplayName("Test Heap Memory Management")
        void testHeapMemoryManagement() {
            System.setProperty("alloc.bytes", "262144"); // 256KB allocations
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long maxHeap = beforeHeap.getMax();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for memory management test
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long usedHeap = afterHeap.getUsed();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should manage heap memory effectively");
            
            // Heap usage should not exceed reasonable limits
            double heapUsagePercent = (double) usedHeap / maxHeap * 100;
            assertTrue(heapUsagePercent < 80, "Heap usage should be under 80%");
            
            System.out.printf("Heap usage: %.1f%% (%d MB / %d MB)%n", 
                heapUsagePercent, usedHeap / 1024 / 1024, maxHeap / 1024 / 1024);
        }

        @Test
        @DisplayName("Test Non-Heap Memory Impact")
        void testNonHeapMemoryImpact() {
            System.setProperty("alloc.bytes", "131072"); // 128KB allocations
            System.setProperty("churn.batch", "300");
            System.setProperty("leak.every", "150");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeNonHeap = memoryBean.getNonHeapMemoryUsage();
            long beforeNonHeapUsed = beforeNonHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for memory management test
            }

            MemoryUsage afterNonHeap = memoryBean.getNonHeapMemoryUsage();
            long afterNonHeapUsed = afterNonHeap.getUsed();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should complete without excessive non-heap usage");
            
            // Non-heap memory growth should be minimal
            long nonHeapGrowth = afterNonHeapUsed - beforeNonHeapUsed;
            assertTrue(nonHeapGrowth < 50 * 1024 * 1024, // 50MB limit
                "Non-heap memory growth should be minimal: " + (nonHeapGrowth / 1024 / 1024) + "MB");
            
            System.out.printf("Non-heap memory growth: %d MB%n", nonHeapGrowth / 1024 / 1024);
        }

        @Test
        @DisplayName("Test Memory Reclamation")
        void testMemoryReclamation() {
            System.setProperty("alloc.bytes", "524288"); // 512KB allocations
            System.setProperty("churn.batch", "100");
            System.setProperty("leak.every", "0"); // No leaks
            System.setProperty("run.seconds", "2");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for memory reclamation test
            }

            // Force aggressive GC
            for (int i = 0; i < 3; i++) {
                System.gc();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should reclaim memory effectively");
            
            // Memory should be largely reclaimed after GC
            long memoryGrowth = afterUsed - beforeUsed;
            assertTrue(memoryGrowth < 20 * 1024 * 1024, // 20MB tolerance
                "Memory should be effectively reclaimed: " + (memoryGrowth / 1024 / 1024) + "MB");
            
            System.out.printf("Memory after reclamation: %d MB growth%n", memoryGrowth / 1024 / 1024);
        }
    }

    @Nested
    @DisplayName("Performance Impact Tests")
    class PerformanceImpactTests {

        @Test
        @DisplayName("Test Allocation Size Impact")
        void testAllocationSizeImpact() {
            int[] allocationSizes = {1024, 16384, 262144, 1048576}; // 1KB to 1MB
            
            for (int allocSize : allocationSizes) {
                System.setProperty("alloc.bytes", String.valueOf(allocSize));
                System.setProperty("churn.batch", "50");
                System.setProperty("leak.every", "25");
                System.setProperty("run.seconds", "2");
                System.setProperty("report.ms", "2000");

                outputStream.reset();
                
                long startTime = System.currentTimeMillis();
                
                try {
                    LeakAndChurn.main(new String[]{});
                } catch (Exception e) {
                    // Expected for performance test
                }
                
                long duration = System.currentTimeMillis() - startTime;
                String output = outputStream.toString();
                
                assertTrue(output.contains("ops="), 
                    String.format("Should handle %d byte allocations", allocSize));
                
                System.out.printf("Allocation size %d: %d ms%n", allocSize, duration);
                
                // Performance should degrade gracefully with larger allocations
                assertTrue(duration < 10000, 
                    String.format("Should complete within 10 seconds for %d byte allocations", allocSize));
            }
        }

        @Test
        @DisplayName("Test Batch Size Impact")
        void testBatchSizeImpact() {
            int[] batchSizes = {10, 100, 500, 1000};
            
            for (int batchSize : batchSizes) {
                System.setProperty("alloc.bytes", "32768");
                System.setProperty("churn.batch", String.valueOf(batchSize));
                System.setProperty("leak.every", String.valueOf(batchSize / 2));
                System.setProperty("run.seconds", "2");
                System.setProperty("report.ms", "2000");

                outputStream.reset();
                
                long startTime = System.currentTimeMillis();
                
                try {
                    LeakAndChurn.main(new String[]{});
                } catch (Exception e) {
                    // Expected for performance test
                }
                
                long duration = System.currentTimeMillis() - startTime;
                String output = outputStream.toString();
                
                assertTrue(output.contains("ops="), 
                    String.format("Should handle batch size %d", batchSize));
                
                System.out.printf("Batch size %d: %d ms%n", batchSize, duration);
                
                // Should complete within reasonable time for all batch sizes
                assertTrue(duration < 15000, 
                    String.format("Should complete within 15 seconds for batch size %d", batchSize));
            }
        }

        @Test
        @DisplayName("Test Leak Frequency Impact")
        void testLeakFrequencyImpact() {
            int[] leakFrequencies = {0, 10, 50, 100, 200};
            
            for (int leakFreq : leakFrequencies) {
                System.setProperty("alloc.bytes", "16384");
                System.setProperty("churn.batch", "100");
                System.setProperty("leak.every", String.valueOf(leakFreq));
                System.setProperty("run.seconds", "2");
                System.setProperty("report.ms", "2000");

                outputStream.reset();
                
                MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
                long beforeUsed = beforeHeap.getUsed();
                
                long startTime = System.currentTimeMillis();
                
                try {
                    LeakAndChurn.main(new String[]{});
                } catch (Exception e) {
                    // Expected for performance test
                }
                
                long duration = System.currentTimeMillis() - startTime;
                
                MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
                long afterUsed = afterHeap.getUsed();
                long memoryGrowth = afterUsed - beforeUsed;
                
                String output = outputStream.toString();
                assertTrue(output.contains("ops="), 
                    String.format("Should handle leak frequency %d", leakFreq));
                
                System.out.printf("Leak frequency %d: %d ms, %d MB memory growth%n", 
                    leakFreq, duration, memoryGrowth / 1024 / 1024);
                
                // Should complete within reasonable time
                assertTrue(duration < 15000, 
                    String.format("Should complete within 15 seconds for leak frequency %d", leakFreq));
                
                // Memory growth should correlate with leak frequency
                if (leakFreq > 0) {
                    assertTrue(memoryGrowth > 0, 
                        String.format("Should show memory growth with leak frequency %d", leakFreq));
                }
            }
        }
    }
}
