package com.vinci.util;

import android.graphics.Bitmap;

/**
 * Created by austinh on 4/7/14.
 */
public class BitmapPool extends AbstractBlockingPool<Bitmap> {
    private final int mWidth;
    private final int mHeight;
    private final Bitmap.Config mConfig;

    public BitmapPool(int capacity, int width, int height, Bitmap.Config config) {
        super(capacity);
        mWidth = width;
        mHeight = height;
        mConfig = config;
    }

    @Override
    protected Bitmap produce() {
        return Bitmap.createBitmap(mWidth, mHeight, mConfig);
    }
}
