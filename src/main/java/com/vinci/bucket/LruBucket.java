package com.vinci.bucket;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.vinci.BucketListener;
import com.vinci.util.AbstractBlockingPool;
import com.vinci.util.BitmapPool;
import com.vinci.util.IoUtil;
import com.vinci.util.Pool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by austinh on 4/7/14.
 */
public class LruBucket extends AbstractBucket {
    private static final int THREAD_POOL_SIZE = 8;

    private final Context mContext;
    private final Pool<Bitmap> mBitmapPool;
    private final Pool<byte[]> mBufferPool = new BufferPool(THREAD_POOL_SIZE, 8192);
    private final Pool<RawImageLoader> mRawImageLoaderPool = new RawImageLoaderPool(THREAD_POOL_SIZE);
    private final LruCache mLruCache;
    private final DrawableKey mLoaderKey = new DrawableKey();
    private final Bitmap.Config mConfig;

    public LruBucket(Context context, String cachePath, int capacity, int width, int height, Bitmap.Config config) {
        super(cachePath, THREAD_POOL_SIZE);

        mContext = context;
        mBitmapPool = new BitmapPool(capacity + 1, width, height, config);
        mLruCache = new LruCache(capacity, mBitmapPool);
        mConfig = config;
    }

    @Override
    public Drawable get(String path, int width, int height, BucketListener listener) {
        // If the LRU cache knows about the path, return the value
        synchronized (mLoaderKey) {
            mLoaderKey.updateHashCode(path, width, height);

            if (mLruCache.containsKey(mLoaderKey)) {
                final Drawable drawable = mLruCache.get(mLoaderKey);

                if (listener != null) {
                    // Notify the listener if we have a valid drawable
                    if (drawable != null) {
                        listener.onLoaded(path, drawable, width, height);
                    } else {
                        // Otherwise, add the listener
                        addListener(path, listener);
                    }
                }

                return drawable;
            } else {
                // Prime the LRU cache
                mLruCache.put(new DrawableKey(path, width, height), null);
            }
        }

        load(path, width, height, listener);

        return null;
    }

    @Override
    public Drawable precache(String path, int width, int height) {
        return get(path, width, height, null);
    }

    @Override
    public Drawable prefetch(String path, int width, int height) {
        return null;
    }

    @Override
    protected Drawable loadFromDisk(String path) {
        // Load the bitmap
        RawImageLoader loader = null;
        try {
            loader = mRawImageLoaderPool.acquire();
            final Bitmap inBitmap = mBitmapPool.acquire();
            final Bitmap bitmap = loader.load(path, mConfig, inBitmap);

            // If we failed to load an image - release the bitmap
            if (bitmap == null) {
                mBitmapPool.release(inBitmap);
                return null;
            } else {
                // Return the drawable
                return new BitmapDrawable(mContext.getResources(), bitmap);
            }
        } catch (IOException e) {
            System.out.println("Unable to load file from disk.");
        } finally {
            if (loader != null) {
                mRawImageLoaderPool.release(loader);
            }
        }

        return null;
    }

    @Override
    protected boolean saveFromWeb(String path, String localPath) {
        InputStream is = null;
        OutputStream os = null;
        byte[] buffer = null;

        try {
            // Connect to the remote resource
            URL url = new URL(path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();

            // Open the HTTP input stream
            is = connection.getInputStream();

            // Open the destination for writing
            final File tempFile = File.createTempFile("img", "tmp", new File(getCachePath()));
            os = new FileOutputStream(tempFile);

            // Grab a byte buffer from out pool
            buffer = mBufferPool.acquire();

            // Save the file
            IoUtil.copy(is, os, buffer);

            // Close the streams
            is.close();
            os.close();

            // Rename the temp file
            if (!new File(tempFile.getAbsolutePath()).renameTo(new File(localPath))) {
                throw new RuntimeException("Unable to move file");
            }

            return true;
        } catch (IOException e) {
            return false;
        } finally {
            // Release the buffer
            if (buffer != null) {
                mBufferPool.release(buffer);
            }

            // Close the input stream
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e("ahahah", "Error closing input stream", e);
                }
            }

            // Close the output stream
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e("ahahah", "Error closing output stream", e);
                }
            }
        }
    }

    @Override
    protected boolean scale(String path, int width, int height, String destination) {
        // For now, just copy the file over
        InputStream is = null;
        OutputStream os = null;
        byte[] buffer = null;

        try {
            // Open the files
            is = new FileInputStream(path);
            os = new FileOutputStream(destination);

            // Acquire a buffer from the pool
            buffer = mBufferPool.acquire();

            // Save the file
            IoUtil.copy(is, os, buffer);

            return true;
        } catch (IOException e) {
            return false;
        } finally {
            // Release the buffer
            if (buffer != null) {
                mBufferPool.release(buffer);
            }

            // Close the input stream
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e("ahahah", "Error closing input stream", e);
                }
            }

            // Close the output stream
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    Log.e("ahahah", "Error closing output stream", e);
                }
            }
        }
    }

    @Override
    public void onLoaded(String path, Drawable drawable, int width, int height) {
        // We've loaded an image - add it into the LRU cache
        synchronized (mLoaderKey) {
            if (drawable == null) {
                System.out.println("derp");
                throw new IllegalStateException("Drawable loaded and is null!");
            }

            mLoaderKey.updateHashCode(path, width, height);
            if (mLruCache.containsKey(mLoaderKey)) {
                mLruCache.put(mLoaderKey, drawable);
            } else if (drawable != null) {
                mBitmapPool.release(((BitmapDrawable) drawable).getBitmap());
            }
        }
    }

    @Override
    public void onFailure(String path, int width, int height) {
        // TODO : austinh : Something
        synchronized (mLoaderKey) {
            mLoaderKey.updateHashCode(path, width, height);
            mLruCache.remove(mLoaderKey);
        }
    }

    private class LruCache extends LinkedHashMap<DrawableKey, Drawable> {
        private final Pool<Bitmap> mBitmapPool;
        private final int mCapacity;

        private LruCache(int capacity, Pool<Bitmap> bitmapPool) {
            super(capacity, 0.75f, true);
            mCapacity = capacity;
            mBitmapPool = bitmapPool;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<DrawableKey, Drawable> eldest) {
            if (size() > mCapacity) {
                final Drawable drawable = eldest.getValue();
                if (drawable != null) {
                    final Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                    if (bitmap != null) {
                        mBitmapPool.release(bitmap);
                    }
                }
                return true;
            }

            return false;
        }
    }

    private static class DrawableKey {
        private String mRemotePath;
        private int mWidth;
        private int mHeight;
        private int mHashCode = -1;

        private DrawableKey() {
        }

        private DrawableKey(String remotePath, int width, int height) {
            mRemotePath = remotePath;
            mWidth = width;
            mHeight = height;
        }

        private void updateHashCode(String path, int width, int height) {
            mRemotePath = path;
            mWidth = width;
            mHeight = height;
            mHashCode = generateKeyHashCode(path, width, height);
        }

        @Override
        public int hashCode() {
            if (mHashCode == -1) {
                mHashCode = generateKeyHashCode(mRemotePath, mWidth, mHeight);
            }

            return mHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }

            if (!(obj instanceof DrawableKey)) {
                return false;
            }

            final DrawableKey objKey = (DrawableKey) obj;
            return mRemotePath.equals(objKey.mRemotePath)
                    && mWidth == objKey.mWidth
                    && mHeight == objKey.mHeight;
        }
    }

    private static int generateKeyHashCode(String remotePath, int width, int height) {
        int hashCode = 17;
        hashCode += 31 * hashCode + remotePath.hashCode();
        hashCode += 31 * hashCode + width;
        hashCode += 31 * hashCode + height;

        return hashCode;
    }

    private static class BufferPool extends AbstractBlockingPool<byte[]> {
        private final int mBufferSize;

        private BufferPool(int capacity, int bufferSize) {
            super(capacity);
            mBufferSize = bufferSize;
        }

        @Override
        protected byte[] produce() {
            return new byte[mBufferSize];
        }
    }

    private static class RawImageLoaderPool extends AbstractBlockingPool<RawImageLoader> {
        private RawImageLoaderPool(int capacity) {
            super(capacity);
        }

        @Override
        protected RawImageLoader produce() {
            return new RawImageLoader();
        }
    }

    private static class RawImageLoader {
        private final BitmapFactory.Options mOptions = new BitmapFactory.Options();
        private byte[] mRawData;
        private int mLength = 0;

        private RawImageLoader() {
            mOptions.inMutable = true;
            mOptions.inSampleSize = 1;
        }

        public Bitmap load(String path, Bitmap.Config config, Bitmap inBitmap) throws IOException {
            mOptions.inPreferredConfig = config;
            mOptions.inBitmap = inBitmap;

            final RandomAccessFile raf = new RandomAccessFile(path, "r");
            if (raf.length() <= 0) {
                mLength = 0;
                return null;
            }

            mLength = (int) raf.length();

            if (mRawData == null || mRawData.length < mLength) {
                mRawData = new byte[mLength];
            } else {
                Arrays.fill(mRawData, mLength, mRawData.length, (byte) 0);
            }

            // Read in the file
            raf.readFully(mRawData, 0, mLength);

            // Return the data
            return BitmapFactory.decodeByteArray(mRawData, 0, mLength, mOptions);
        }
    }
}
