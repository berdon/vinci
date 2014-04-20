package com.vinci.bucket;

import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.vinci.Bucket;
import com.vinci.BucketListener;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by austinh on 4/7/14.
 */
public abstract class AbstractBucket implements Bucket, BucketListener {
    private final static String TAG = AbstractBucket.class.getSimpleName();
    private final static int DEFAULT_POOL_SIZE = 1;
    private final static int NO_SIZE = -1;

    private final String mCachePath;
    private final Handler mMainHandler;
    private final ExecutorService mExecutorService;
    private final Set<Runnable> mLoaders = new HashSet<Runnable>();
    private final LoaderRunnable mLoaderKey = new LoaderRunnable();
    private final Map<String, Set<BucketListener>> mListenerMap = new HashMap<String, Set<BucketListener>>();

    protected AbstractBucket(String cachePath) {
        this(cachePath, DEFAULT_POOL_SIZE);
    }

    protected AbstractBucket(String cachePath, int threadPoolSize) {
        this(cachePath, threadPoolSize, new Handler(Looper.getMainLooper()));
    }

    /* package */ AbstractBucket(String cachePath, int threadPoolSize, Handler handler) {
        // Make sure we don't have to add / later on
        if (!cachePath.endsWith("/")) {
            cachePath += "/";
        }
        mCachePath = cachePath;

        mMainHandler = handler;
        mExecutorService = new ThreadPoolExecutor(
                threadPoolSize,
                threadPoolSize,
                10,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingDeque<Runnable>() {
                    @Override
                    public boolean add(Runnable runnable) {
                        super.addFirst(runnable);
                        return true;
                    }

                    @Override
                    public boolean offer(Runnable runnable) {
                        return offerFirst(runnable);
                    }

                    @Override
                    public boolean offer(Runnable runnable, long timeout, TimeUnit unit) throws InterruptedException {
                        return offerFirst(runnable, timeout, unit);
                    }

                    @Override
                    public void put(Runnable runnable) throws InterruptedException {
                        putFirst(runnable);
                    }
                });

        final File cacheDirectory = new File(cachePath);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs();
        }
    }

    protected abstract Drawable loadFromDisk(String path);

    protected abstract boolean saveFromWeb(String path, String localPath);

    protected abstract boolean scale(String path, int width, int height, String destination);

    @Override
    public void destroy() {
        mExecutorService.shutdownNow();
    }

    public String getCachePath() {
        return mCachePath;
    }

    protected void load(String remotePath, int width, int height) {
        load(remotePath, width, height, null);
    }

    protected void load(String remotePath, int width, int height, BucketListener listener) {
        // Check for the loader already running
        synchronized (mLoaders) {
            if (listener != null) {
                addListener(remotePath, listener);
            }

            mLoaderKey.updateHashCode(remotePath, width, height);
            if (mLoaders.contains(mLoaderKey)) {
                return;
            }

            final Runnable loader = new LoaderRunnable(remotePath, width, height);
            mLoaders.add(loader);
            mExecutorService.execute(loader);
        }
    }

    protected String getFilename(String remotePath) {
        return getFilename(remotePath, NO_SIZE, NO_SIZE);
    }

    protected String getFilename(String remotePath, int width, int height) {
        if (remotePath == null) {
            throw new IllegalArgumentException("Invalid remote path.");
        }

        if (width == NO_SIZE && height == NO_SIZE) {
            return String.format("%s%d.jpg", mCachePath, remotePath.hashCode());
        } else {
            return String.format("%s%d-%d-%d.jpg", mCachePath, width, height, remotePath.hashCode());
        }
    }

    protected void addListener(String remotePath, BucketListener listener) {
        synchronized (mListenerMap) {
            Set<BucketListener> listeners = mListenerMap.get(remotePath);
            if (listeners == null) {
                listeners = new LinkedHashSet<BucketListener>();
                mListenerMap.put(remotePath, listeners);
            }

            listeners.add(listener);
        }
    }

    private void notifyListeners(String remotePath, Drawable drawable, int width, int height) {
        final Set<BucketListener> listeners;
        synchronized (mListenerMap) {
            // Grab the listeners
            listeners = mListenerMap.get(remotePath);

            // Remove the listeners from the map
            mListenerMap.remove(remotePath);
        }

        if (listeners != null && listeners.size() > 0) {
            mMainHandler.post(new NotifyRunnable(remotePath, drawable, width, height, listeners));
        }
    }

    private class NotifyRunnable implements Runnable {
        private final String mRemotePath;
        private final int mWidth;
        private final int mHeight;
        private final Drawable mDrawable;
        private final Set<BucketListener> mListeners;

        private NotifyRunnable(String remotePath, Drawable drawable, int width, int height, Set<BucketListener> listeners) {
            mRemotePath = remotePath;
            mWidth = width;
            mHeight = height;
            mDrawable = drawable;
            mListeners = listeners;
        }

        @Override
        public void run() {
            if (mDrawable != null) {
                for (BucketListener listener : mListeners) {
                    listener.onLoaded(mRemotePath, mDrawable, mWidth, mHeight);
                }
            } else {
                for (BucketListener listener : mListeners) {
                    listener.onFailure(mRemotePath, mWidth, mHeight);
                }
            }
        }
    }

    private class LoaderRunnable implements Runnable {
        private String mRemotePath;
        private int mWidth;
        private int mHeight;
        private int mHashCode = -1;

        private LoaderRunnable() {
        }

        private LoaderRunnable(String remotePath, int width, int height) {
            mRemotePath = remotePath;
            mWidth = width;
            mHeight = height;
        }

        @Override
        public void run() {
            try {
                Drawable drawable = null;
                try {
                    drawable = execute();
                } catch (RuntimeException e) {
                    Log.e(TAG, String.format("Error loading drawable! %s", mRemotePath));
                }

                synchronized (mLoaders) {
                    // First notify ourselves
                    if (drawable != null) {
                        onLoaded(mRemotePath, drawable, mWidth, mHeight);
                    } else {
                        onFailure(mRemotePath, mWidth, mHeight);
                    }

                    // Now notify listeners
                    notifyListeners(mRemotePath, drawable, mWidth, mHeight);
                }
            } finally {
                // Remove the loader from the loaders set
                mLoaders.remove(this);
            }
        }

        private Drawable execute() {
            final String scaledFilename = getFilename(mRemotePath, mWidth, mHeight);
            final File scaledFile = new File(scaledFilename);

            // Exact size
            if (scaledFile.exists()) {
                final Drawable d = loadFromDisk(scaledFilename);
                int i = 0;
                i++;
                return d;
            }

            final String unscaledFilename = getFilename(mRemotePath);
            final File unscaledFile = new File(unscaledFilename);

            // If no unscaled, download
            if (!unscaledFile.exists()) {
                saveFromWeb(mRemotePath, unscaledFilename);
            }

            // Unscaled - scale and load
            if (unscaledFile.exists()) {
                if (scale(unscaledFilename, mWidth, mHeight, scaledFilename)) {
                    final Drawable d = loadFromDisk(scaledFilename);
                    return d;
                }

                System.out.print("Scale call failed");
            }


            return null;
        }

        private void updateHashCode(String remotePath, int width, int height) {
            mRemotePath = remotePath;
            mWidth = width;
            mHeight = height;
            mHashCode = generateLoaderHashCode(mRemotePath, mWidth, mHeight);
        }

        @Override
        public int hashCode() {
            if (mHashCode == -1) {
                mHashCode = generateLoaderHashCode(mRemotePath, mWidth, mHeight);
            }

            return mHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof LoaderRunnable)) {
                return false;
            }

            final LoaderRunnable objKey = (LoaderRunnable) obj;
            return mRemotePath.equals(objKey.mRemotePath)
                    && mWidth == objKey.mWidth
                    && mHeight == objKey.mHeight;
        }

        @Override
        public String toString() {
            return "LoaderRunnable{" +
                    "mRemotePath='" + mRemotePath + '\'' +
                    ", mWidth=" + mWidth +
                    ", mHeight=" + mHeight +
                    ", mHashCode=" + mHashCode +
                    '}';
        }
    }

    private static int generateLoaderHashCode(String remotePath, int width, int height) {
        int hashCode = 17;
        hashCode += 31 * hashCode + remotePath.hashCode();
        hashCode += 31 * hashCode + width;
        hashCode += 31 * hashCode + height;

        return hashCode;
    }
}
