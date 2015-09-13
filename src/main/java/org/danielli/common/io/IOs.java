package org.danielli.common.io;

import java.io.Closeable;
import java.io.IOException;

/**
 * IO工具类。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class IOs {

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }
}
