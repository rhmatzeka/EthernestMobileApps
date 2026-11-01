package id.rahmat.projekakhir.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AppExecutors {

    /** Screen-level background work (load a screen, send a transaction). */
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);

    /**
     * Fan-out of small network calls made from inside IO tasks, such as one
     * balance request per chain. Kept separate from IO so an IO task waiting on
     * these can never starve the pool it runs in. Idle threads time out.
     */
    private static final ExecutorService NETWORK = createNetworkPool();

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }

    public static ExecutorService network() {
        return NETWORK;
    }

    private static ExecutorService createNetworkPool() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                8, 8, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
