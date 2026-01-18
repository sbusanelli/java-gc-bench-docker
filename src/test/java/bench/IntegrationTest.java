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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for GC benchmark with system components
 * Tests integration with JVM, memory management, and system resources
 */
@DisplayName("Integration Tests")
class IntegrationTest {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    private List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outputStream));
        // Force GC before each test
        System.gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Test JVM Integration")
    void testJVMIntegration() {
        System.setProperty("alloc.bytes", "32768");
        System.setProperty("churn.batch", "200");
        System.setProperty("leak.every", "50");
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1500");

        // Get initial JVM stats
        long initialUptime = runtimeBean.getUptime();
        MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
        long initialGcCount = getTotalGCCount();
        long initialGcTime = getTotalGCTime();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for integration test
        }

        // Get final JVM stats
        long finalUptime = runtimeBean.getUptime();
        MemoryUsage finalHeap = memoryBean.getHeapMemoryUsage();
        long finalGcCount = getTotalGCCount();
        long finalGcTime = getTotalGCTime();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should integrate with JVM");
        
        // Verify JVM integration
        assertTrue(finalUptime > initialUptime, "JVM uptime should increase");
        assertTrue(finalHeap.getUsed() > initialHeap.getUsed(), "Heap usage should increase");
        assertTrue(finalGcCount >= initialGcCount, "GC count should not decrease");
        assertTrue(finalGcTime >= initialGcTime, "GC time should not decrease");
        
        System.out.printf("JVM Integration: Uptime %d ms, Heap %d MB, GC %d runs (%d ms)%n",
            finalUptime - initialUptime,
            (finalHeap.getUsed() - initialHeap.getUsed()) / 1024 / 1024,
            finalGcCount - initialGcCount,
            finalGcTime - initialGcTime);
    }

    @Test
    @DisplayName("Test Memory Management Integration")
    void testMemoryManagementIntegration() {
        System.setProperty("alloc.bytes", "65536");
        System.setProperty("churn.batch", "150");
        System.setProperty("leak.every", "75");
        System.setProperty("run.seconds", "4");
        System.setProperty("report.ms", "2000");

        // Get initial memory stats
        MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
        MemoryUsage initialNonHeap = memoryBean.getNonHeapMemoryUsage();
        long initialMaxHeap = initialHeap.getMax();
        long initialUsedHeap = initialHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for memory integration test
        }

        // Get final memory stats
        MemoryUsage finalHeap = memoryBean.getHeapMemoryUsage();
        MemoryUsage finalNonHeap = memoryBean.getNonHeapMemoryUsage();
        long finalUsedHeap = finalHeap.getUsed();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should integrate with memory management");
        assertTrue(output.contains("leak="), "Should track memory leaks");
        
        // Verify memory management integration
        assertTrue(finalUsedHeap > initialUsedHeap, "Heap usage should increase with leaks");
        
        double heapUsagePercent = (double) finalUsedHeap / initialMaxHeap * 100;
        assertTrue(heapUsagePercent < 90, "Heap usage should be reasonable: " + heapUsagePercent + "%");
        
        long memoryGrowth = finalUsedHeap - initialUsedHeap;
        assertTrue(memoryGrowth > 5 * 1024 * 1024, // 5MB minimum growth
            "Should show memory growth: " + (memoryGrowth / 1024 / 1024) + "MB");
        
        System.out.printf("Memory Integration: Heap %d MB (%.1f%%), Growth %d MB%n",
            finalUsedHeap / 1024 / 1024,
            heapUsagePercent,
            memoryGrowth / 1024 / 1024);
    }

    @Test
    @DisplayName("Test Garbage Collection Integration")
    void testGarbageCollectionIntegration() {
        System.setProperty("alloc.bytes", "16384");
        System.setProperty("churn.batch", "300");
        System.setProperty("leak.every", "0"); // No leaks for GC testing
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        // Get initial GC stats
        long initialGcCount = getTotalGCCount();
        long initialGcTime = getTotalGCTime();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for GC integration test
        }

        // Force GC to see final state
        System.gc();
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get final GC stats
        long finalGcCount = getTotalGCCount();
        long finalGcTime = getTotalGCTime();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should integrate with garbage collection");
        
        // Verify GC integration
        assertTrue(finalGcCount > initialGcCount, "GC should run during memory churn");
        assertTrue(finalGcTime > initialGcTime, "GC time should increase");
        
        long gcRuns = finalGcCount - initialGcCount;
        long gcTimeIncrease = finalGcTime - initialGcTime;
        double avgGcTime = gcRuns > 0 ? (double) gcTimeIncrease / gcRuns : 0;
        
        assertTrue(gcRuns > 0, "Should trigger garbage collection");
        assertTrue(avgGcTime > 0, "GC should take time");
        
        System.out.printf("GC Integration: %d runs, %d ms total, %.2f ms avg%n",
            gcRuns, gcTimeIncrease, avgGcTime);
    }

    @Test
    @DisplayName("Test System Resource Integration")
    void testSystemResourceIntegration() {
        System.setProperty("alloc.bytes", "32768");
        System.setProperty("churn.batch", "200");
        System.setProperty("leak.every", "100");
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1500");

        // Get initial system resource stats
        Runtime runtime = Runtime.getRuntime();
        long initialTotalMemory = runtime.totalMemory();
        long initialFreeMemory = runtime.freeMemory();
        int initialProcessors = runtime.availableProcessors();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for system integration test
        }

        // Get final system resource stats
        long finalTotalMemory = runtime.totalMemory();
        long finalFreeMemory = runtime.freeMemory();

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should integrate with system resources");
        
        // Verify system resource integration
        assertTrue(finalTotalMemory >= initialTotalMemory, "Total memory should not decrease");
        assertTrue(initialProcessors > 0, "Should detect available processors");
        
        long usedMemory = finalTotalMemory - finalFreeMemory;
        long memoryGrowth = usedMemory - (initialTotalMemory - initialFreeMemory);
        
        assertTrue(memoryGrowth > 0, "Memory usage should increase");
        
        System.out.printf("System Integration: Processors %d, Memory %d MB, Growth %d MB%n",
            initialProcessors,
            usedMemory / 1024 / 1024,
            memoryGrowth / 1024 / 1024);
    }

    @Test
    @DisplayName("Test Configuration Integration")
    void testConfigurationIntegration() {
        // Test with various configuration combinations
        String[][] configs = {
            {"16384", "100", "50", "2", "1000"},
            {"32768", "200", "100", "3", "1500"},
            {"65536", "150", "75", "2", "2000"}
        };
        
        for (int i = 0; i < configs.length; i++) {
            String[] config = configs[i];
            System.setProperty("alloc.bytes", config[0]);
            System.setProperty("churn.batch", config[1]);
            System.setProperty("leak.every", config[2]);
            System.setProperty("run.seconds", config[3]);
            System.setProperty("report.ms", config[4]);

            outputStream.reset();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for configuration test
            }

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), 
                String.format("Config %d should integrate properly", i + 1));
            assertTrue(output.contains("leak="), 
                String.format("Config %d should track leaks", i + 1));
            
            System.out.printf("Config %d (%s/%s/%s): Integrated successfully%n", 
                i + 1, config[0], config[1], config[2]);
        }
    }

    @Nested
    @DisplayName("End-to-End Integration Tests")
    class EndToEndIntegrationTests {

        @Test
        @DisplayName("Test Complete Workflow Integration")
        void testCompleteWorkflowIntegration() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "50");
            System.setProperty("run.seconds", "5");
            System.setProperty("report.ms", "2000");

            // Get initial system state
            long initialUptime = runtimeBean.getUptime();
            MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
            long initialGcCount = getTotalGCCount();
            long initialGcTime = getTotalGCTime();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for E2E integration test
            }

            // Get final system state
            long finalUptime = runtimeBean.getUptime();
            MemoryUsage finalHeap = memoryBean.getHeapMemoryUsage();
            long finalGcCount = getTotalGCCount();
            long finalGcTime = getTotalGCTime();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should complete full workflow");
            assertTrue(output.contains("leak="), "Should track leaks throughout workflow");
            
            // Verify complete workflow integration
            assertTrue(finalUptime > initialUptime, "Workflow should take time");
            assertTrue(finalHeap.getUsed() > initialHeap.getUsed(), "Memory should be used");
            assertTrue(finalGcCount > initialGcCount, "GC should run during workflow");
            assertTrue(finalGcTime > initialGcTime, "GC time should accumulate");
            
            // Should show multiple reports
            long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
            assertTrue(reportCount >= 2, "Should generate multiple workflow reports");
            
            System.out.printf("E2E Integration: %d reports, %d ms runtime, %d MB memory, %d GC runs%n",
                reportCount,
                finalUptime - initialUptime,
                (finalHeap.getUsed() - initialHeap.getUsed()) / 1024 / 1024,
                finalGcCount - initialGcCount);
        }

        @Test
        @DisplayName("Test Stress Integration")
        void testStressIntegration() {
            System.setProperty("alloc.bytes", "65536"); // Large allocations
            System.setProperty("churn.batch", "300"); // High batch
            System.setProperty("leak.every", "25"); // Frequent leaks
            System.setProperty("run.seconds", "4"); // Longer run
            System.setProperty("report.ms", "1000"); // Frequent reporting

            // Get initial stress metrics
            MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
            long initialGcCount = getTotalGCCount();
            long initialGcTime = getTotalGCTime();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for stress integration test
            }

            // Get final stress metrics
            MemoryUsage finalHeap = memoryBean.getHeapMemoryUsage();
            long finalGcCount = getTotalGCCount();
            long finalGcTime = getTotalGCTime();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should handle stress integration");
            
            // Verify stress integration
            long memoryGrowth = finalHeap.getUsed() - initialHeap.getUsed();
            long gcRuns = finalGcCount - initialGcCount;
            long gcTimeIncrease = finalGcTime - initialGcTime;
            
            assertTrue(memoryGrowth > 20 * 1024 * 1024, // 20MB minimum stress
                "Should show memory stress: " + (memoryGrowth / 1024 / 1024) + "MB");
            assertTrue(gcRuns > 5, "Should trigger multiple GC runs under stress");
            assertTrue(gcTimeIncrease > 100, "Should accumulate GC time under stress");
            
            System.out.printf("Stress Integration: %d MB memory, %d GC runs, %d ms GC time%n",
                memoryGrowth / 1024 / 1024, gcRuns, gcTimeIncrease);
        }

        @Test
        @DisplayName("Test Recovery Integration")
        void testRecoveryIntegration() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "100"); // Moderate leaks
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1500");

            // Phase 1: Create memory pressure
            MemoryUsage beforePressure = memoryBean.getHeapMemoryUsage();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for recovery test
            }

            MemoryUsage afterPressure = memoryBean.getHeapMemoryUsage();
            long memoryPressure = afterPressure.getUsed() - beforePressure.getUsed();

            // Phase 2: Test recovery
            System.gc();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            MemoryUsage afterRecovery = memoryBean.getHeapMemoryUsage();
            long memoryRecovered = memoryPressure - (afterRecovery.getUsed() - beforePressure.getUsed());

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should complete pressure phase");
            
            // Verify recovery integration
            assertTrue(memoryPressure > 5 * 1024 * 1024, // 5MB minimum pressure
                "Should create memory pressure: " + (memoryPressure / 1024 / 1024) + "MB");
            assertTrue(memoryRecovered > 0, "Should recover some memory: " + (memoryRecovered / 1024 / 1024) + "MB");
            
            double recoveryRatio = (double) memoryRecovered / memoryPressure;
            assertTrue(recoveryRatio > 0.1, "Should recover at least 10% of memory");
            
            System.out.printf("Recovery Integration: %d MB pressure, %d MB recovered (%.1f%%)%n",
                memoryPressure / 1024 / 1024,
                memoryRecovered / 1024 / 1024,
                recoveryRatio * 100);
        }
    }

    @Nested
    @DisplayName("Component Integration Tests")
    class ComponentIntegrationTests {

        @Test
        @DisplayName("Test Memory Monitor Integration")
        void testMemoryMonitorIntegration() {
            System.setProperty("alloc.bytes", "16384");
            System.setProperty("churn.batch", "250");
            System.setProperty("leak.every", "125");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            // Monitor memory throughout execution
            MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
            long maxMemoryUsed = initialHeap.getUsed();
            long minMemoryUsed = initialHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for monitor integration test
            }

            MemoryUsage finalHeap = memoryBean.getHeapMemoryUsage();
            
            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should integrate with memory monitoring");
            
            // Verify memory monitoring integration
            assertTrue(finalHeap.getUsed() > initialHeap.getUsed(), "Memory monitoring should detect growth");
            
            System.out.printf("Memory Monitor Integration: Initial %d MB, Final %d MB%n",
                initialHeap.getUsed() / 1024 / 1024,
                finalHeap.getUsed() / 1024 / 1024);
        }

        @Test
        @DisplayName("Test Performance Monitor Integration")
        void testPerformanceMonitorIntegration() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "0");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1500");

            long startTime = System.currentTimeMillis();
            long startGcTime = getTotalGCTime();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for performance monitor test
            }

            long endTime = System.currentTimeMillis();
            long endGcTime = getTotalGCTime();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should integrate with performance monitoring");
            
            long duration = endTime - startTime;
            long gcTime = endGcTime - startGcTime;
            double performanceRatio = gcTime > 0 ? (double) duration / gcTime : 0;
            
            // Verify performance monitoring integration
            assertTrue(duration > 0, "Should measure execution time");
            assertTrue(gcTime >= 0, "Should measure GC time");
            
            System.out.printf("Performance Monitor: %d ms total, %d ms GC, %.2f ratio%n",
                duration, gcTime, performanceRatio);
        }

        @Test
        @DisplayName("Test Resource Monitor Integration")
        void testResourceMonitorIntegration() {
            System.setProperty("alloc.bytes", "65536");
            System.setProperty("churn.batch", "150");
            System.setProperty("leak.every", "75");
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "2000");

            Runtime runtime = Runtime.getRuntime();
            long initialTotalMemory = runtime.totalMemory();
            long initialFreeMemory = runtime.freeMemory();
            int initialProcessors = runtime.availableProcessors();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for resource monitor test
            }

            long finalTotalMemory = runtime.totalMemory();
            long finalFreeMemory = runtime.freeMemory();

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should integrate with resource monitoring");
            
            // Verify resource monitoring integration
            assertTrue(finalTotalMemory >= initialTotalMemory, "Total memory should be tracked");
            assertTrue(initialProcessors > 0, "Processors should be detected");
            
            long memoryUsed = finalTotalMemory - finalFreeMemory;
            System.out.printf("Resource Monitor: %d processors, %d MB memory used%n",
                initialProcessors, memoryUsed / 1024 / 1024);
        }
    }

    // Helper methods
    private long getTotalGCCount() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private long getTotalGCTime() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }
}
