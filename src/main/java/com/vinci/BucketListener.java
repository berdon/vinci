package com.vinci;

import android.graphics.drawable.Drawable;

/**
 * Created by austinh on 4/7/14.
 */
public interface BucketListener {
    void onLoaded(String path, Drawable drawable, int width, int height);
    void onFailure(String path, int width, int height);
}
