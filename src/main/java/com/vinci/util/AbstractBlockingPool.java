package com.vinci.util;

import java.util.LinkedHashSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by austinh on 4/7/14.
 */
public abstract class AbstractBlockingPool<ResourceType> implements Pool<ResourceType> {
    private static final String TAG = AbstractBlockingPool.class.getSimpleName();

    private int mCapacity;
    private LinkedHashSet<ResourceType> mPool = new LinkedHashSet<ResourceType>();
    private LinkedHashSet<ResourceType> mAcquired = new LinkedHashSet<ResourceType>();
    private BlockingDeque<ResourceType> mAvailable = new LinkedBlockingDeque<ResourceType>();

    public AbstractBlockingPool(int capacity) {
        mCapacity = capacity;
    }

    @Override
    public ResourceType acquire() {
        final ResourceType resource;
        if (mAvailable.isEmpty() && mPool.size() < mCapacity) {
            resource = produce();

            mPool.add(resource);
        } else {
            try {
                resource = mAvailable.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        mAcquired.add(resource);

        return resource;
    }

    @Override
    public void release(ResourceType resource) {
        if (!mPool.contains(resource)) {
            throw new IllegalArgumentException("Resource did not come from this pool");
        }

        if (!mAcquired.contains(resource)) {
            throw new IllegalArgumentException("Resource has not been acquired.");
        }

        if (mAcquired.remove(resource)) {
            mAvailable.add(resource);
        }
    }

    @Override
    public synchronized void drain() {
        mPool.clear();
        mAvailable.clear();
        mAcquired.clear();
    }

    /* package */ int getCreatableAmount() {
        return mCapacity - mPool.size();
    }

    /* package */ int getAvailable() {
        return mCapacity - mAcquired.size();
    }

    /* package */ int getUnavailable() {
        return mAcquired.size();
    }

    protected abstract ResourceType produce();
}
