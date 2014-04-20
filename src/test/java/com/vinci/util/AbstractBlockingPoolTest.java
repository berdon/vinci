package com.vinci.util;

import android.util.Log;
import junit.framework.TestCase;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit test for the {@link com.vinci.util.AbstractBlockingPool}
 */
public class AbstractBlockingPoolTest extends TestCase {
    private static final String TAG = AbstractBlockingPoolTest.class.getSimpleName();
    private static final int DEFAULT_POOL_SIZE = 5;
    private static final int EXECUTOR_POOL_SIZE = 32;
    private static final int ASYNC_TASKS = 512;
    private static final int FACTORY_OVERFLOW_TIMEOUT = 1; // minutes
    private static final int MADNESS_TIMEOUT = 3; // minutes

    private AbstractBlockingPool<ResourceTest> mDefaultResourcePool;
    private ResourceFactoryTest mDefaultResourceFactory = new ResourceFactoryTest();

    @Override
    public void setUp() throws Exception {
        // Create the default resource pool
        mDefaultResourcePool = new AbstractBlockingPool<ResourceTest>(DEFAULT_POOL_SIZE) {
            @Override
            protected ResourceTest produce() {
                return mDefaultResourceFactory.createResource();
            }
        };
    }

    @Override
    public void tearDown() throws Exception {
        mDefaultResourcePool.drain();
    }

    /**
     * Tests the initial conditions of the resource pool.
     */
    public void testInitialConditions() {
        assertTrue(mDefaultResourcePool.getAvailable() == DEFAULT_POOL_SIZE);
        assertTrue(mDefaultResourcePool.getUnavailable() == 0);
    }

    /**
     * Tests the factory and resource acquiring.
     * @throws Exception
     */
    public void testFactoryAcquiring() throws Exception {
        for (int i = 1; i <= DEFAULT_POOL_SIZE; i++) {
            // Acquire an item
            mDefaultResourcePool.acquire();

            // Check availability
            assertTrue(mDefaultResourcePool.getAvailable() == (DEFAULT_POOL_SIZE - i));
            assertTrue(mDefaultResourcePool.getUnavailable() == i);

            // Check creatable count
            assertTrue(mDefaultResourcePool.getCreatableAmount() == (DEFAULT_POOL_SIZE - mDefaultResourceFactory.mCreated));

            // Check the factory count
            assertTrue(mDefaultResourceFactory.mCreated == i);
        }
    }

    /**
     * Tests for releasing a resource that didn't come from the pool.
     * @throws Exception
     */
    public void testInvalidResource() throws Exception {
        try {
            mDefaultResourcePool.release(new ResourceTest());
            fail("Pool allows for releasing of arbitrary resources");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    /**
     * Tests for pool/factory creation overflowing as a result of multiple threads making simultaneous
     * requests.
     * @throws Exception
     */
    public void testFactoryOverflow() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(EXECUTOR_POOL_SIZE);
        final List<Future> futures = new LinkedList<Future>();
        final AtomicInteger runningTasks = new AtomicInteger(ASYNC_TASKS);

        // Watchdog to check for a negative creatable amount (ie. threads overran the pool/factory)
        futures.add(executor.submit(new Runnable() {
            @Override
            public void run() {
                while (runningTasks.get() > 0) {
                    assertTrue(mDefaultResourcePool.getCreatableAmount() >= 0);
                }
            }
        }));

        // Iterate between 0 - ASYNC_TASKS times
        for (int i = 0; i < ASYNC_TASKS; i++) {
            final int threadId = i;
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Tasks remaining: " + runningTasks.get());

                    // Acquire
                    System.out.printf("%d] Acquiring resource%n", threadId);
                    ResourceTest resource = mDefaultResourcePool.acquire();

                    try {
                        // Sleep a random period of time
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        fail("Thread killed during factory overflow test");
                    }

                    // Release the resource
                    System.out.printf("%d] Releasing resource%n", threadId);
                    mDefaultResourcePool.release(resource);

                    runningTasks.decrementAndGet();
                }
            }));
        }

        executor.shutdown();

        // Await termination and die after FACTORY_OVERFLOW_TIMEOUT minutes
        assertTrue(executor.awaitTermination(FACTORY_OVERFLOW_TIMEOUT, TimeUnit.MINUTES));

        // Handle unforeseen exceptions
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    /**
     * Checks to make sure the blocking pool blocks appropriately.
     * @throws Exception
     */
    public void testBlocking() throws Exception {
        // Fill the pool
        fillPool();

        Thread acquireThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // Grab a resource
                mDefaultResourcePool.acquire();
                fail("Pool failed to block while acquiring a resource");
            }
        });

        // Start the acquire thread
        acquireThread.start();

        // Wait a little bit to let it block
        Thread.sleep(1000);

        // Kill the thread
        acquireThread.interrupt();

        // Join the thread to completion
        acquireThread.join();
    }

    /**
     * Spawns EXECUTOR_POOL_SIZE threads that acquire and release ASYNC_TASKS resources at random
     * times. This checks for possible deadlock conditions.
     * @throws Exception
     */
    public void testMadness() throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(EXECUTOR_POOL_SIZE);
        final List<Future> futures = new LinkedList<Future>();
        final AtomicInteger runningTasks = new AtomicInteger(ASYNC_TASKS);

        // Iterate between 0 - ASYNC_TASKS times
        for (int i = 0; i < ASYNC_TASKS; i++) {
            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Tasks remaining: " + runningTasks.get());

                    // Wait an arbitrary amount so all of the threads don't start at the same time
                    try {
                        Thread.sleep(Math.round(Math.random() * 1000));
                    } catch (InterruptedException e) {
                        fail("Thread killed during madness test");
                    }

                    // Acquire
                    long time = System.currentTimeMillis();
                    long oldTime;
                    System.out.printf("[%d] Acquiring resource%n", time);
                    ResourceTest resource = mDefaultResourcePool.acquire();

                    oldTime = time;
                    time = System.currentTimeMillis();
                    System.out.printf("[%d] Resource acquired (delta: %d)%n", System.currentTimeMillis(), time - oldTime);

                    try {
                        // Sleep a random period of time
                        Thread.sleep(Math.round(Math.random() * 1000));
                    } catch (InterruptedException e) {
                        fail("Thread killed during madness test");
                    }

                    // Release the resource
                    mDefaultResourcePool.release(resource);

                    System.out.printf("[%d] Resource released (delta: %d)%n", System.currentTimeMillis(), System.currentTimeMillis() - time);

                    runningTasks.decrementAndGet();
                }
            }));
        }

        executor.shutdown();

        // Await termination and die after MADNESS_TIMEOUT minutes
        assertTrue(executor.awaitTermination(MADNESS_TIMEOUT, TimeUnit.MINUTES));

        // Handle unforeseen exceptions
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    private Set<ResourceTest> fillPool() throws InterruptedException {
        final Set<ResourceTest> resources = new HashSet<ResourceTest>();
        for (int i = 0; i < DEFAULT_POOL_SIZE; i++) {
            resources.add(mDefaultResourcePool.acquire());
        }

        return resources;
    }

    private static final class ResourceFactoryTest {
        private int mCreated = 0;

        public ResourceTest createResource() {
            mCreated++;
            return new ResourceTest();
        }
    }

    private static final class ResourceTest {
    }
}
