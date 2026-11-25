import java.util.concurrent.*;

// Four requests hog a CPU for 10 seconds on a 4-carrier scheduler.
// A fifth, trivial request arrives once a second. How long does each one wait?
//   java -Djdk.virtualThreadScheduler.parallelism=4 -Djdk.virtualThreadScheduler.maxPoolSize=4 Starve.java virtual
//   java Starve.java platform
public class Starve {
    static final int HOGS = 4;
    static final long HOG_MS = 10_000;

    public static void main(String[] args) throws Exception {
        boolean virtual = args.length == 0 || args[0].equals("virtual");
        ExecutorService pool = virtual
            ? Executors.newVirtualThreadPerTaskExecutor()
            : Executors.newCachedThreadPool();   // one OS thread per request, the pre-Loom way
        System.out.println("java " + Runtime.version() + ", " + (virtual ? "virtual threads, 4 carriers" : "one platform thread per request"));

        for (int i = 0; i < HOGS; i++) {
            pool.submit(() -> {
                long end = System.nanoTime() + HOG_MS * 1_000_000;
                long x = 0;
                while (System.nanoTime() < end) x++;   // CPU only, never blocks
                return x;
            });
        }
        Thread.sleep(100); // let the hogs start

        for (int i = 1; i <= 5; i++) {
            long sent = System.nanoTime();
            int n = i;
            pool.submit(() -> {
                long waitedMs = (System.nanoTime() - sent) / 1_000_000;
                System.out.printf("request %d waited %,d ms%n", n, waitedMs);
            });
            Thread.sleep(1000);
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
    }
}
