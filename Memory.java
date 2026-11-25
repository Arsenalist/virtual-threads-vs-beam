import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

// One request keeps allocating and never lets go. Four normal requests keep working
// (each loop: allocate 1 MB, sleep 10 ms). What happens to the normal ones?
//   java -Xmx512m Memory.java
public class Memory {
    public static void main(String[] args) throws Exception {
        System.out.println("java " + Runtime.version() + ", heap limit 512 MB, virtual threads");
        var pool = Executors.newVirtualThreadPerTaskExecutor();
        var stop = new AtomicBoolean();
        long t0 = System.nanoTime();

        for (int i = 1; i <= 4; i++) {
            int n = i;
            pool.submit(() -> {
                long worst = 0, last = System.nanoTime(); int loops = 0;
                try {
                    while (!stop.get()) {
                        byte[][] work = new byte[16][];
                        for (int k = 0; k < 16; k++) work[k] = new byte[1 << 16];   // 1 MB of short-lived work
                        Thread.sleep(10);
                        long now = System.nanoTime();
                        worst = Math.max(worst, (now - last) / 1_000_000);
                        last = now; loops++;
                    }
                    System.out.printf("normal request %d finished %,d loops, worst stall %,d ms%n", n, loops, worst);
                } catch (OutOfMemoryError e) {
                    System.out.printf("normal request %d DIED with OutOfMemoryError at %.1f s (worst stall before that %,d ms)%n",
                        n, (System.nanoTime() - t0) / 1e9, worst);
                } catch (InterruptedException e) {}
            });
        }
        Thread.sleep(500);

        pool.submit(() -> {
            List<byte[]> kept = new ArrayList<>();
            try {
                int holdMb = args.length > 0 ? Integer.parseInt(args[0]) : Integer.MAX_VALUE;   // no arg: allocate until it dies
                while (kept.size() / 16 < holdMb) { for (int k = 0; k < 16; k++) kept.add(new byte[1 << 16]); Thread.sleep(1); }
                System.out.printf("bad request now holding %,d MB and keeping it%n", kept.size() / 16);
                Thread.sleep(60_000);
            } catch (OutOfMemoryError e) {
                int mb = kept.size() / 16;
                kept = null;
                System.out.printf("bad request died with OutOfMemoryError at %.1f s holding %,d MB%n",
                    (System.nanoTime() - t0) / 1e9, mb);
            } catch (InterruptedException e) {}
        });

        Thread.sleep(Long.getLong("runMs", 15_000));
        stop.set(true);
        Thread.sleep(200);
        pool.shutdownNow();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }
}
