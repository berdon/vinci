package com.vinci.bucket;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import com.vinci.Bucket;
import com.vinci.BucketListener;
import com.vinci.util.FileUtil;
import junit.framework.TestCase;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by austinh on 4/8/14.
 */
public class AbstractBucketTest extends TestCase {
    private final static String CACHE_PATH = "/tmp/images";
    private final static int THREAD_POOL_SIZE = 8;
    private final static int REQUEST_ORDER_COUNT = 100;
    private Bucket mBucket;

    @Override
    public void tearDown() throws Exception {
        if (mBucket != null) {
            mBucket.destroy();
        }

        // Delete the image cache
        FileUtil.deleteDirectory(new File(CACHE_PATH));
    }

    public synchronized void testSingleGet() throws Exception {
        mBucket = new SimpleBucket(CACHE_PATH, THREAD_POOL_SIZE, Mockito.mock(Handler.class));

        final SimpleBucket spy = (SimpleBucket) Mockito.spy(mBucket);
        final class Event {
            public volatile boolean occured = false;
        }
        final Event event = new Event();
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                event.occured = true;
                notify();
                return null;
            }
        }).when(spy).onLoaded(Mockito.anyString(), Mockito.any(Drawable.class), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                fail("Image failed to load");
                return null;
            }
        }).when(spy).onFailure(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());

        spy.get("derp.jpg", 100, 100, null);

        wait(5000);

        assertTrue(event.occured);
    }

    public synchronized void testRequestOrdering() throws Exception {
        // Specify a thread pool size of 1 so we can guarantee ordering
        mBucket = new SimpleBucket(CACHE_PATH, 1, Mockito.mock(Handler.class));

        final SimpleBucket spy = (SimpleBucket) Mockito.spy(mBucket);
        final AtomicBoolean isPrimed = new AtomicBoolean(false);
        final Object lock = new Object();

        Mockito.doAnswer(new Answer() {
            @Override
            public Drawable answer(InvocationOnMock invocation) throws Throwable {
                // We wait until all requests have been loaded
                synchronized (lock) {
                    if (!isPrimed.get()) {
                        lock.wait();
                    }
                }

                return Mockito.mock(Drawable.class);
            }
        }).when(spy).loadFromDisk(Mockito.anyString());

        final AtomicInteger mExpectedIndex = new AtomicInteger(0);

        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                // 0 is a throw away, after that we should expect every single request to be of a lower index
                final String path = (String) invocation.getArguments()[0];

                System.out.println("Request for " + path);

                final int index = Integer.parseInt(path);
                if (index == 0) {
                    mExpectedIndex.set(REQUEST_ORDER_COUNT - 1);
                    return null;
                }

                if (index != mExpectedIndex.getAndDecrement()) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }

                // Need to handle the final case
                if (index == 1) {
                    synchronized (lock) {
                        lock.notifyAll();
                    }
                }

                return null;
            }
        }).when(spy).onLoaded(Mockito.anyString(), Mockito.any(Drawable.class), Mockito.anyInt(), Mockito.anyInt());
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                fail("Image failed to load");
                return null;
            }
        }).when(spy).onFailure(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt());

        for (int i = 0; i < REQUEST_ORDER_COUNT; i++) {
            spy.get(String.valueOf(i), 100, 100, null);
        }

        synchronized (lock) {
            // Mark as primed
            isPrimed.set(true);

            // Start the loaders
            lock.notifyAll();

            // Wait for things to finish
            lock.wait(100000);
        }

        assertTrue(mExpectedIndex.get() == 0);
    }

    private static class SimpleBucket extends AbstractBucket {
        private SimpleBucket(String cachePath, int threadPoolSize, Handler handler) {
            super(cachePath, threadPoolSize, handler);
        }

        @Override
        public Drawable get(String path, int width, int height, BucketListener listener) {
            load(path, width, height, listener);
            return null;
        }

        @Override
        public Drawable precache(String path, int width, int height) {
            load(path, width, height);
            return null;
        }

        @Override
        public Drawable prefetch(String path, int width, int height) {
            load(path, width, height);
            return null;
        }

        @Override
        protected Drawable loadFromDisk(String path) {
            return Mockito.mock(Drawable.class);
        }

        @Override
        protected boolean saveFromWeb(String path, String localPath) {
            // Write a blank file to the path
            try {
                new File(localPath).createNewFile();
            } catch (IOException e) {
                fail(e.getMessage());
            }

            return true;
        }

        @Override
        protected boolean scale(String path, int width, int height, String destination) {
            // Write a blank file to the path
            try {
                new File(destination).createNewFile();
            } catch (IOException e) {
                fail(e.getMessage());
            }

            return true;
        }

        @Override
        public void onLoaded(String path, Drawable drawable, int width, int height) { }

        @Override
        public void onFailure(String path, int width, int height) { }
    }
}
