package org.danielli.common.io;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.zip.GZIPOutputStream;

/**
 * {@link java.io.File}工具类。
 *
 * @author Daniel Li
 * @since 09 August 2015
 */
public class Files {

    public static final char EXTENSION_SEPARATOR = '.';

    private static final char UNIX_SEPARATOR = '/';

    private static final char WINDOWS_SEPARATOR = '\\';

    public static void compressFile(File source, File destination, int bufferSize) throws IOException {
        if (source == null || !source.exists()) {
            return;
        }

        if (destination == null) {
            return;
        }

        InputStream input = null;
        OutputStream output = null;
        try {
            input = new BufferedInputStream(new FileInputStream(source));
            output = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(destination)));
            final byte[] inbuf = new byte[bufferSize];
            int n;
            while ((n = input.read(inbuf)) != -1) {
                output.write(inbuf, 0, n);
            }
        } finally {
            IOs.closeQuietly(input);
            IOs.closeQuietly(output);
        }
    }

    public static void copyFile(File source, File destination) throws IOException {
        if (source == null || !source.exists()) {
            return;
        }

        if (destination == null) {
            return;
        }

        if (!destination.exists()) {
            destination.createNewFile();
        }
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(destination);
            FileChannel inputChannel = input.getChannel();
            FileChannel outputChannel = output.getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            IOs.closeQuietly(input);
            IOs.closeQuietly(output);
        }
    }

    public static boolean isExtension(String filename, String[] extensions) {
        if (filename == null) {
            return false;
        }
        if (extensions == null || extensions.length == 0) {
            return (indexOfExtension(filename) == -1);
        }
        String fileExt = getExtension(filename);
        for (String extension : extensions) {
            if (fileExt.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    public static String removeExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int index = indexOfExtension(filename);
        if (index == -1) {
            return filename;
        } else {
            return filename.substring(0, index);
        }
    }

    public static String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int index = indexOfExtension(filename);
        if (index == -1) {
            return "";
        } else {
            return filename.substring(index + 1);
        }
    }

    public static int indexOfExtension(String filename) {
        if (filename == null) {
            return -1;
        }
        int extensionPos = filename.lastIndexOf(EXTENSION_SEPARATOR);
        int lastSeparator = indexOfLastSeparator(filename);
        return (lastSeparator > extensionPos ? -1 : extensionPos);
    }

    public static int indexOfLastSeparator(String filename) {
        if (filename == null) {
            return -1;
        }
        int lastUnixPos = filename.lastIndexOf(UNIX_SEPARATOR);
        int lastWindowsPos = filename.lastIndexOf(WINDOWS_SEPARATOR);
        return Math.max(lastUnixPos, lastWindowsPos);
    }
}
