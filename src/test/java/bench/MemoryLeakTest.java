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
 * Memory leak detection and analysis tests
 * Tests memory leak simulation and detection capabilities
 */
@DisplayName("Memory Leak Tests")
class MemoryLeakTest {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

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
    @DisplayName("Test Controlled Memory Leak")
    void testControlledMemoryLeak() {
        System.setProperty("alloc.bytes", "32768"); // 32KB allocations
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "10"); // Leak every 10th allocation
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for memory leak test
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        long memoryLeaked = afterUsed - beforeUsed;

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations");
        assertTrue(output.contains("leak="), "Should track memory leaks");
        
        // Should detect significant memory leak
        assertTrue(memoryLeaked > 5 * 1024 * 1024, // 5MB minimum leak
            "Should detect memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
        
        // Leak count should be positive
        assertTrue(output.contains("leak=") && !output.contains("leak=0"), 
            "Should show positive leak count");
        
        System.out.printf("Controlled leak detected: %d MB%n", memoryLeaked / 1024 / 1024);
    }

    @Test
    @DisplayName("Test No Memory Leak Scenario")
    void testNoMemoryLeakScenario() {
        System.setProperty("alloc.bytes", "32768");
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "0"); // No leaks
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for no-leak test
        }

        // Force GC to reclaim memory
        System.gc();
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        long memoryGrowth = afterUsed - beforeUsed;

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations");
        assertTrue(output.contains("leak=0"), "Should show no leaks");
        
        // Memory growth should be minimal without leaks
        assertTrue(memoryGrowth < 10 * 1024 * 1024, // 10MB tolerance
            "Memory growth should be minimal without leaks: " + (memoryGrowth / 1024 / 1024) + "MB");
        
        System.out.printf("No leak scenario: %d MB memory growth%n", memoryGrowth / 1024 / 1024);
    }

    @Test
    @DisplayName("Test Gradual Memory Leak")
    void testGradualMemoryLeak() {
        System.setProperty("alloc.bytes", "16384"); // 16KB allocations
        System.setProperty("churn.batch", "50");
        System.setProperty("leak.every", "25"); // Leak every 25th allocation
        System.setProperty("run.seconds", "5");
        System.setProperty("report.ms", "1500");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for gradual leak test
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        long memoryLeaked = afterUsed - beforeUsed;

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations");
        assertTrue(output.contains("leak="), "Should track gradual memory leaks");
        
        // Should detect gradual memory leak
        assertTrue(memoryLeaked > 3 * 1024 * 1024, // 3MB minimum leak
            "Should detect gradual memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
        
        // Should show multiple reports indicating gradual leak progression
        long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
        assertTrue(reportCount >= 2, "Should show leak progression over time");
        
        System.out.printf("Gradual leak detected: %d MB over %d reports%n", 
            memoryLeaked / 1024 / 1024, reportCount);
    }

    @Test
    @DisplayName("Test Rapid Memory Leak")
    void testRapidMemoryLeak() {
        System.setProperty("alloc.bytes", "65536"); // 64KB allocations
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "5"); // Leak every 5th allocation
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
        long beforeUsed = beforeHeap.getUsed();

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for rapid leak test
        }

        MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
        long afterUsed = afterHeap.getUsed();
        long memoryLeaked = afterUsed - beforeUsed;

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations");
        assertTrue(output.contains("leak="), "Should track rapid memory leaks");
        
        // Should detect rapid memory leak
        assertTrue(memoryLeaked > 10 * 1024 * 1024, // 10MB minimum leak
            "Should detect rapid memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
        
        System.out.printf("Rapid leak detected: %d MB%n", memoryLeaked / 1024 / 1024);
    }

    @Test
    @DisplayName("Test Memory Leak Detection Accuracy")
    void testMemoryLeakDetectionAccuracy() {
        System.setProperty("alloc.bytes", "32768");
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "20"); // Known leak frequency
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for accuracy test
        }

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform operations");
        assertTrue(output.contains("leak="), "Should track memory leaks");
        
        // Extract leak count from output
        String[] lines = output.split("\n");
        int finalLeakCount = 0;
        for (String line : lines) {
            if (line.contains("DONE")) {
                String[] parts = line.split("leak=");
                if (parts.length > 1) {
                    finalLeakCount = Integer.parseInt(parts[1].trim());
                }
            }
        }
        
        // Leak count should be reasonable for the given configuration
        assertTrue(finalLeakCount > 0, "Should detect positive leak count");
        assertTrue(finalLeakCount < 1000, "Leak count should be reasonable");
        
        System.out.printf("Detected %d leaks with expected frequency%n", finalLeakCount);
    }

    @Nested
    @DisplayName("Leak Pattern Tests")
    class LeakPatternTests {

        @Test
        @DisplayName("Test Periodic Memory Leak")
        void testPeriodicMemoryLeak() {
            System.setProperty("alloc.bytes", "16384");
            System.setProperty("churn.batch", "50");
            System.setProperty("leak.every", "100"); // Leak every 100th allocation
            System.setProperty("run.seconds", "4");
            System.setProperty("report.ms", "1500");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for periodic leak test
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long memoryLeaked = afterUsed - beforeUsed;

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should perform operations");
            assertTrue(output.contains("leak="), "Should track periodic memory leaks");
            
            // Should detect periodic memory leak
            assertTrue(memoryLeaked > 2 * 1024 * 1024, // 2MB minimum leak
                "Should detect periodic memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
            
            // Should show consistent leak pattern
            long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
            assertTrue(reportCount >= 2, "Should show periodic leak pattern");
            
            System.out.printf("Periodic leak detected: %d MB over %d reports%n", 
                memoryLeaked / 1024 / 1024, reportCount);
        }

        @Test
        @DisplayName("Test Burst Memory Leak")
        void testBurstMemoryLeak() {
            System.setProperty("alloc.bytes", "65536");
            System.setProperty("churn.batch", "200");
            System.setProperty("leak.every", "1"); // Leak every allocation
            System.setProperty("run.seconds", "2");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for burst leak test
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long memoryLeaked = afterUsed - beforeUsed;

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should perform operations");
            assertTrue(output.contains("leak="), "Should track burst memory leaks");
            
            // Should detect significant burst leak
            assertTrue(memoryLeaked > 20 * 1024 * 1024, // 20MB minimum leak
                "Should detect burst memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
            
            System.out.printf("Burst leak detected: %d MB%n", memoryLeaked / 1024 / 1024);
        }

        @Test
        @DisplayName("Test Intermittent Memory Leak")
        void testIntermittentMemoryLeak() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "100");
            System.setProperty("leak.every", "75"); // Leak every 75th allocation
            System.setProperty("run.seconds", "4");
            System.setProperty("report.ms", "2000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for intermittent leak test
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long memoryLeaked = afterUsed - beforeUsed;

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should perform operations");
            assertTrue(output.contains("leak="), "Should track intermittent memory leaks");
            
            // Should detect intermittent memory leak
            assertTrue(memoryLeaked > 5 * 1024 * 1024, // 5MB minimum leak
                "Should detect intermittent memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
            
            System.out.printf("Intermittent leak detected: %d MB%n", memoryLeaked / 1024 / 1024);
        }
    }

    @Nested
    @DisplayName("Leak Impact Tests")
    class LeakImpactTests {

        @Test
        @DisplayName("Test Memory Leak Impact on Performance")
        void testMemoryLeakImpactOnPerformance() {
            System.setProperty("alloc.bytes", "16384");
            System.setProperty("churn.batch", "100");
            System.setProperty("leak.every", "50"); // Moderate leak frequency
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            long startTime = System.currentTimeMillis();
            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for impact test
            }

            long endTime = System.currentTimeMillis();
            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long memoryLeaked = afterUsed - beforeUsed;
            long duration = endTime - startTime;

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should perform operations despite leaks");
            
            // Performance should not be severely impacted by leaks
            assertTrue(duration < 10000, "Should complete within 10 seconds despite leaks");
            
            // Should detect memory leak
            assertTrue(memoryLeaked > 3 * 1024 * 1024, // 3MB minimum leak
                "Should detect memory leak: " + (memoryLeaked / 1024 / 1024) + "MB");
            
            System.out.printf("Performance impact: %d ms duration, %d MB leaked%n", 
                duration, memoryLeaked / 1024 / 1024);
        }

        @Test
        @DisplayName("Test Memory Leak Impact on GC")
        void testMemoryLeakImpactOnGC() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "100");
            System.setProperty("leak.every", "25"); // High leak frequency
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for GC impact test
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long memoryLeaked = afterUsed - beforeUsed;

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should complete despite GC pressure from leaks");
            
            // Should detect significant memory leak
            assertTrue(memoryLeaked > 8 * 1024 * 1024, // 8MB minimum leak
                "Should detect memory leak affecting GC: " + (memoryLeaked / 1024 / 1024) + "MB");
            
            // Should show consistent reporting despite GC pressure
            long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
            assertTrue(reportCount >= 2, "Should maintain reporting despite GC pressure");
            
            System.out.printf("GC impact: %d MB leaked, %d reports generated%n", 
                memoryLeaked / 1024 / 1024, reportCount);
        }

        @Test
        @DisplayName("Test Memory Leak Threshold Detection")
        void testMemoryLeakThresholdDetection() {
            System.setProperty("alloc.bytes", "65536");
            System.setProperty("churn.batch", "50");
            System.setProperty("leak.every", "10"); // Known leak frequency
            System.setProperty("run.seconds", "3");
            System.setProperty("report.ms", "1000");

            MemoryUsage beforeHeap = memoryBean.getHeapMemoryUsage();
            long beforeUsed = beforeHeap.getUsed();

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for threshold test
            }

            MemoryUsage afterHeap = memoryBean.getHeapMemoryUsage();
            long afterUsed = afterHeap.getUsed();
            long memoryLeaked = afterUsed - beforeUsed;

            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should perform operations");
            assertTrue(output.contains("leak="), "Should track memory leaks");
            
            // Define leak threshold (5MB)
            long leakThreshold = 5 * 1024 * 1024;
            
            if (memoryLeaked > leakThreshold) {
                System.out.printf("WARNING: Memory leak threshold exceeded! %d MB > %d MB%n", 
                    memoryLeaked / 1024 / 1024, leakThreshold / 1024 / 1024);
            } else {
                System.out.printf("Memory leak within threshold: %d MB <= %d MB%n", 
                    memoryLeaked / 1024 / 1024, leakThreshold / 1024 / 1024);
            }
            
            // Should detect leak above threshold
            assertTrue(memoryLeaked > leakThreshold, 
                "Should detect memory leak above threshold: " + (memoryLeaked / 1024 / 1024) + "MB");
        }
    }
}
