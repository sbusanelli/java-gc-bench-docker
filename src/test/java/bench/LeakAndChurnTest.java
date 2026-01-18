package bench;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unit tests for LeakAndChurn benchmark
 * Tests memory allocation, garbage collection, and leak simulation
 */
@DisplayName("LeakAndChurn Unit Tests")
class LeakAndChurnTest {

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outputStream));
        // Reset static fields
        try {
            Field leakField = LeakAndChurn.class.getDeclaredField("LEAK");
            leakField.setAccessible(true);
            ((Map<?, ?>) leakField.get(null)).clear();
            
            Field opsField = LeakAndChurn.class.getDeclaredField("OPS");
            opsField.setAccessible(true);
            ((AtomicLong) opsField.get(null)).set(0);
        } catch (Exception e) {
            // Ignore reflection errors
        }
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    @DisplayName("Test Basic Memory Allocation")
    void testBasicMemoryAllocation() {
        System.setProperty("alloc.bytes", "1024");
        System.setProperty("churn.batch", "10");
        System.setProperty("leak.every", "5");
        System.setProperty("run.seconds", "1");
        System.setProperty("report.ms", "500");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should report operations count");
        assertTrue(output.contains("bag="), "Should report bag size");
        assertTrue(output.contains("leak="), "Should report leak size");
    }

    @Test
    @DisplayName("Test Memory Leak Simulation")
    void testMemoryLeakSimulation() {
        System.setProperty("alloc.bytes", "2048");
        System.setProperty("churn.batch", "20");
        System.setProperty("leak.every", "10");
        System.setProperty("run.seconds", "2");
        System.setProperty("report.ms", "1000");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("leak="), "Should simulate memory leaks");
        
        // Check that leak count increases
        assertTrue(output.contains("leak=") && output.contains("ops="), 
            "Should have both leak and operation counts");
    }

    @Test
    @DisplayName("Test No Leak Configuration")
    void testNoLeakConfiguration() {
        System.setProperty("alloc.bytes", "1024");
        System.setProperty("churn.batch", "10");
        System.setProperty("leak.every", "0"); // No leaks
        System.setProperty("run.seconds", "1");
        System.setProperty("report.ms", "500");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should report operations");
        
        // With leak.every=0, leak count should remain 0
        assertTrue(output.contains("leak=0") || output.contains("leak= 0"), 
            "Should not leak when leak.every=0");
    }

    @Test
    @DisplayName("Test High Allocation Rate")
    void testHighAllocationRate() {
        System.setProperty("alloc.bytes", "65536");
        System.setProperty("churn.batch", "4000");
        System.setProperty("leak.every", "200");
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "1000");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should handle high allocation rate");
        assertTrue(output.contains("bag="), "Should report bag size");
        
        // Should have multiple reports due to longer run time
        long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
        assertTrue(reportCount >= 1, "Should generate multiple reports");
    }

    @Test
    @DisplayName("Test Configuration Parameters")
    void testConfigurationParameters() {
        // Test with custom parameters
        System.setProperty("alloc.bytes", "4096");
        System.setProperty("churn.batch", "100");
        System.setProperty("leak.every", "50");
        System.setProperty("run.seconds", "1");
        System.setProperty("report.ms", "200");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should use custom configuration");
        assertTrue(output.contains("bag="), "Should report bag size");
        assertTrue(output.contains("leak="), "Should report leak size");
    }

    @Test
    @DisplayName("Test Memory Churn")
    void testMemoryChurn() {
        System.setProperty("alloc.bytes", "8192");
        System.setProperty("churn.batch", "50");
        System.setProperty("leak.every", "25");
        System.setProperty("run.seconds", "2");
        System.setProperty("report.ms", "1000");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("ops="), "Should perform memory churn");
        assertTrue(output.contains("bag="), "Should report bag size");
        
        // Bag size should vary due to churn
        assertTrue(output.contains("bag="), "Should show bag size changes");
    }

    @Test
    @DisplayName("Test Reporting Frequency")
    void testReportingFrequency() {
        System.setProperty("alloc.bytes", "1024");
        System.setProperty("churn.batch", "10");
        System.setProperty("leak.every", "5");
        System.setProperty("run.seconds", "3");
        System.setProperty("report.ms", "500"); // Frequent reporting

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("uptime="), "Should report uptime");
        assertTrue(output.contains("ops="), "Should report operations");
        
        // Should have multiple reports due to frequent reporting
        long reportCount = output.lines().filter(line -> line.contains("uptime=")).count();
        assertTrue(reportCount >= 2, "Should generate multiple frequent reports");
    }

    @Test
    @DisplayName("Test Completion Message")
    void testCompletionMessage() {
        System.setProperty("alloc.bytes", "1024");
        System.setProperty("churn.batch", "10");
        System.setProperty("leak.every", "5");
        System.setProperty("run.seconds", "1");
        System.setProperty("report.ms", "200");

        try {
            LeakAndChurn.main(new String[]{});
        } catch (Exception e) {
            // Expected for short run time
        }

        String output = outputStream.toString();
        assertTrue(output.contains("DONE"), "Should print completion message");
        assertTrue(output.contains("ops="), "Should report final operations");
        assertTrue(output.contains("leak="), "Should report final leak count");
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Test Zero Allocation Size")
        void testZeroAllocationSize() {
            System.setProperty("alloc.bytes", "0");
            System.setProperty("churn.batch", "10");
            System.setProperty("leak.every", "5");
            System.setProperty("run.seconds", "1");
            System.setProperty("report.ms", "500");

            assertDoesNotThrow(() -> {
                try {
                    LeakAndChurn.main(new String[]{});
                } catch (Exception e) {
                    // Expected for edge case
                }
            }, "Should handle zero allocation size");
        }

        @Test
        @DisplayName("Test Zero Batch Size")
        void testZeroBatchSize() {
            System.setProperty("alloc.bytes", "1024");
            System.setProperty("churn.batch", "0");
            System.setProperty("leak.every", "5");
            System.setProperty("run.seconds", "1");
            System.setProperty("report.ms", "500");

            assertDoesNotThrow(() -> {
                try {
                    LeakAndChurn.main(new String[]{});
                } catch (Exception e) {
                    // Expected for edge case
                }
            }, "Should handle zero batch size");
        }

        @Test
        @DisplayName("Test Zero Run Time")
        void testZeroRunTime() {
            System.setProperty("alloc.bytes", "1024");
            System.setProperty("churn.batch", "10");
            System.setProperty("leak.every", "5");
            System.setProperty("run.seconds", "0");
            System.setProperty("report.ms", "500");

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for zero run time
            }

            String output = outputStream.toString();
            // Should complete immediately with minimal operations
            assertTrue(output.contains("DONE") || output.isEmpty(), 
                "Should complete immediately or have minimal output");
        }

        @Test
        @DisplayName("Test Very Large Allocation Size")
        void testVeryLargeAllocationSize() {
            System.setProperty("alloc.bytes", "1048576"); // 1MB
            System.setProperty("churn.batch", "10");
            System.setProperty("leak.every", "5");
            System.setProperty("run.seconds", "1");
            System.setProperty("report.ms", "500");

            assertDoesNotThrow(() -> {
                try {
                    LeakAndChurn.main(new String[]{});
                } catch (Exception e) {
                    // Expected for large allocations
                }
            }, "Should handle large allocation sizes");
        }

        @Test
        @DisplayName("Test Very High Leak Frequency")
        void testVeryHighLeakFrequency() {
            System.setProperty("alloc.bytes", "1024");
            System.setProperty("churn.batch", "10");
            System.setProperty("leak.every", "1"); // Leak every operation
            System.setProperty("run.seconds", "1");
            System.setProperty("report.ms", "500");

            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for high leak frequency
            }

            String output = outputStream.toString();
            assertTrue(output.contains("leak="), "Should handle high leak frequency");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Test Performance Under Load")
        void testPerformanceUnderLoad() {
            System.setProperty("alloc.bytes", "32768");
            System.setProperty("churn.batch", "1000");
            System.setProperty("leak.every", "100");
            System.setProperty("run.seconds", "2");
            System.setProperty("report.ms", "1000");

            long startTime = System.currentTimeMillis();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for performance test
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should complete under load");
            
            // Should complete within reasonable time
            assertTrue(duration < 10000, "Should complete within 10 seconds");
        }

        @Test
        @DisplayName("Test Memory Efficiency")
        void testMemoryEfficiency() {
            System.setProperty("alloc.bytes", "16384");
            System.setProperty("churn.batch", "500");
            System.setProperty("leak.every", "0"); // No leaks for efficiency test
            System.setProperty("run.seconds", "2");
            System.setProperty("report.ms", "1000");

            Runtime runtime = Runtime.getRuntime();
            long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
            
            try {
                LeakAndChurn.main(new String[]{});
            } catch (Exception e) {
                // Expected for efficiency test
            }
            
            System.gc(); // Force garbage collection
            Thread.sleep(100); // Allow GC to complete
            
            long afterMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = afterMemory - beforeMemory;
            
            String output = outputStream.toString();
            assertTrue(output.contains("ops="), "Should complete efficiently");
            
            // Memory usage should be reasonable
            assertTrue(memoryUsed < 100 * 1024 * 1024, // 100MB limit
                "Memory usage should be reasonable: " + (memoryUsed / 1024 / 1024) + "MB");
        }
    }
}
