package Inventory_Concurrent_Processing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>Concurrency Configuration</h2>
 *
 * Configures the bounded thread pool used for concurrent CSV processing.
 *
 * <h3>Key design features:</h3>
 * <ul>
 *   <li><b>Bounded Queue:</b> {@link ArrayBlockingQueue} of size 30 prevents unbounded memory growth.</li>
 *   <li><b>Controlled Task Submission / Backpressure:</b> {@link ThreadPoolExecutor.CallerRunsPolicy}
 *       ensures that if the worker queue is saturated, the submitting stream-reading thread executes
 *       the chunk itself, naturally slowing down CSV ingestion until workers catch up.</li>
 *   <li><b>Named Threads:</b> Dedicated daemon thread factory for clear observability and profiling.</li>
 * </ul>
 */
@Configuration
public class ConcurrencyConfig {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyConfig.class);

    private static final int QUEUE_CAPACITY = 30;

    @Bean(name = "inventoryThreadPool", destroyMethod = "shutdown")
    public ThreadPoolExecutor inventoryThreadPool() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        // Core pool sized to CPU cores; maximum capped to prevent database connection exhaustion
        int corePoolSize = Math.max(4, cpuCores);
        int maxPoolSize = Math.max(corePoolSize, Math.min(16, cpuCores * 2));

        log.info("Initializing Inventory ThreadPool: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                corePoolSize, maxPoolSize, QUEUE_CAPACITY);

        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "inv-worker-" + threadNumber.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
