package com.vinci.canvas;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import com.vinci.Canvas;

import java.lang.ref.WeakReference;

/**
 * Created by austinh on 4/7/14.
 */
public class MutableCanvas implements Canvas {
    private CanvasType mCanvasType;
    private WeakReference<Object> mCanvas;

    public MutableCanvas(CanvasType canvasType, Object canvas) {
        mCanvasType = canvasType;
        mCanvas = new WeakReference<Object>(canvas);
    }

    public CanvasType getCanvasType() {
        return mCanvasType;
    }

    public void setCanvasType(CanvasType canvasType) {
        mCanvasType = canvasType;
    }

    @Override
    public void setDrawable(Drawable drawable) {
        final Object canvas = mCanvas.get();
        if (canvas == null) {
            return;
        }

        switch (mCanvasType) {
            case ImageView:
                ((ImageView) canvas).setImageDrawable(drawable);
                break;
        }
    }

    public enum CanvasType {
        ImageView
    }
}
