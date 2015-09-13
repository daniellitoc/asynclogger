package org.danielli.logging;

/**
 * 日志文件写入器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface Logger {

    void write(LoggerEvent event);

    void write(LoggerEvent event, boolean endOfBatch);

    void close();

    /**
     * 日志文件写入器。
     *
     * @author Daniel Li
     * @since 8 August 2015
     */
    interface Filter {

        boolean isWrite(LoggerEvent event);

    }

}
