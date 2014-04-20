package com.vinci;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.vinci.canvas.MutableCanvas;

/**
 * Created by austinh on 4/7/14.
 */
public class Palette {
    private final Object mLock = new Object();
    private Drawable mStub;
    private Canvas mCanvas;

    public Palette into(ImageView imageView) {
        initCanvas(MutableCanvas.CanvasType.ImageView, imageView);
        applyStub();
        return this;
    }

    public Palette stub(Drawable drawable) {
        mStub = drawable;
        applyStub();
        return this;
    }

    private void applyStub() {
        if (mStub != null && mCanvas != null) {
            mCanvas.setDrawable(mStub);
        }
    }

    private void initCanvas(MutableCanvas.CanvasType canvasType, Object canvas) {
        if (mCanvas == null) {
            synchronized (mLock) {
                if (mCanvas == null) {
                    mCanvas = new MutableCanvas(canvasType, canvas);
                }
            }
        }
    }
}
