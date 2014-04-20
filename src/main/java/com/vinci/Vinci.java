package com.vinci;

import android.content.Context;
import android.graphics.Bitmap;
import com.vinci.bucket.LruBucket;

/**
 * Created by austinh on 4/7/14.
 */
public class Vinci {
    public static Bucket createBucket(Context context, String cachePath, int width, int height, int capacity) {
        return new LruBucket(context, cachePath, capacity, width, height, Bitmap.Config.RGB_565);
    }
}
