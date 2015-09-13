package org.danielli.logging.handler;

/**
 * 日志文件。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface FileHandler {

    void close();

    void write(byte[] data);

    void flush();

    String getName();

    long length();

    long initialTime();
}
