package com.vinci.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by austinh on 4/8/14.
 */
public class FileUtil {
    //-----------------------------------------------------------------------
    /**
     * Recursively delete a directory.
     *
     * @param directory  directory to delete
     * @throws IOException in case deletion is unsuccessful
     */
    public static void deleteDirectory(File directory)
            throws IOException {
        if (!directory.exists()) {
            return;
        }

        // Rename the file first
        final File renamedDirectory = new File(directory.getAbsolutePath() + System.currentTimeMillis());
        if (!directory.renameTo(renamedDirectory)) {
            throw new IOException("Unable to rename file prior to delete: " + directory);
        }

        cleanDirectory(renamedDirectory);
        if (!renamedDirectory.delete()) {
            String message =
                    "Unable to delete directory " + directory + ".";
            throw new IOException(message);
        }
    }

    /**
     * Clean a directory without deleting it.
     *
     * @param directory directory to clean
     * @throws IOException in case cleaning is unsuccessful
     */
    public static void cleanDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            String message = directory + " does not exist";
            throw new IllegalArgumentException(message);
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null) {  // null if security restricted
            throw new IOException("Failed to list contents of " + directory);
        }

        IOException exception = null;
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }
        }

        if (null != exception) {
            throw exception;
        }
    }

    /**
     * Delete a file. If file is a directory, delete it and all sub-directories.
     * <p>
     * The difference between File.delete() and this method are:
     * <ul>
     * <li>A directory to be deleted does not have to be empty.</li>
     * <li>You get exceptions when a file or directory cannot be deleted.
     *      (java.io.File methods returns a boolean)</li>
     * </ul>
     *
     * @param file  file or directory to delete, not null
     * @throws NullPointerException if the directory is null
     * @throws IOException in case deletion is unsuccessful
     */
    public static void forceDelete(File file) throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            if (!file.exists()) {
                throw new FileNotFoundException("File does not exist: " + file);
            }

            // Rename the file first
            final File renamedFile = new File(file.getAbsolutePath() + System.currentTimeMillis());
            if (!file.renameTo(renamedFile)) {
                throw new IOException("Unable to rename file prior to delete: " + file);
            }

            if (!renamedFile.delete()) {
                String message =
                        "Unable to delete file: " + file;
                throw new IOException(message);
            }
        }
    }
}
