package com.vinci;

import android.graphics.drawable.Drawable;

/**
 * Created by austinh on 4/7/14.
 */
public interface Bucket {
    Drawable get(String path, int width, int height, BucketListener listener);
    Drawable precache(String path, int width, int height);
    Drawable prefetch(String path, int width, int height);
    void destroy();
}
