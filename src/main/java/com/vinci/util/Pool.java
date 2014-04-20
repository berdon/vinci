package com.vinci.util;

/**
 * Created by austinh on 4/7/14.
 */
public interface Pool<ResourceType> {
    ResourceType acquire();
    void release(ResourceType resource);
    void drain();
}
