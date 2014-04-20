package com.vinci.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by austinh on 4/7/14.
 */
public class IoUtil {
    private static final int BUFFER = 8192;

    public static void copy(InputStream is, OutputStream os) throws IOException {
        copy(is, os, new byte[BUFFER]);
    }

    public static void copy(InputStream is, OutputStream os, byte[] buffer) throws IOException {
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }
}
