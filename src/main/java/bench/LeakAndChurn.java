package bench;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class LeakAndChurn {
    private static final Map<Long, byte[]> LEAK = new HashMap<>();
    private static final AtomicLong OPS = new AtomicLong();

    public static void main(String[] args) throws Exception {
        final int bytesPerAlloc = Integer.parseInt(System.getProperty("alloc.bytes", "65536"));
        final int churnBatch    = Integer.parseInt(System.getProperty("churn.batch", "4000"));
        final int leakEveryN    = Integer.parseInt(System.getProperty("leak.every", "200"));
        final long runSeconds   = Long.parseLong(System.getProperty("run.seconds", "180"));
        final long reportEveryMs= Long.parseLong(System.getProperty("report.ms", "2000"));

        long start = System.nanoTime();
        long end = start + runSeconds * 1_000_000_000L;
        long nextReport = start + reportEveryMs * 1_000_000L;

        ArrayList<byte[]> bag = new ArrayList<>(churnBatch);

        while (System.nanoTime() < end) {
            bag.clear();
            for (int i = 0; i < churnBatch; i++) {
                byte[] block = new byte[bytesPerAlloc];
                block[0] = 1; block[block.length-1] = 1;
                bag.add(block);

                long n = OPS.incrementAndGet();
                if (leakEveryN > 0 && (n % leakEveryN == 0)) {
                    LEAK.put(n, block);
                }
            }
            int drops = ThreadLocalRandom.current().nextInt(churnBatch / 4);
            for (int d = 0; d < drops; d++) {
                bag.set(ThreadLocalRandom.current().nextInt(bag.size()), null);
            }

            long now = System.nanoTime();
            if (now >= nextReport) {
                double uptime = (now - start) / 1e9;
                System.out.printf("uptime=%.1fs ops=%d bag=%d leak=%d%n",
                        uptime, OPS.get(), bag.size(), LEAK.size());
                nextReport = now + reportEveryMs * 1_000_000L;
            }
        }
        System.out.println("DONE ops=" + OPS.get() + " leak=" + LEAK.size());
    }
}
