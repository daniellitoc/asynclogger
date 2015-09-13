package org.danielli.logging.handler.support;

import org.danielli.common.clock.Clock;
import org.danielli.common.io.IOs;
import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.logging.exception.LoggerException;
import org.danielli.logging.handler.FileHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 默认日志文件。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class DefaultFileHandler implements FileHandler {

    protected final String fileName;
    protected final boolean isAppend;
    protected final ByteBuffer buffer;
    protected final Clock clock;
    protected final ExceptionHandler handler;
    protected FileChannel fileChannel;
    protected long size;
    protected long initialTime;

    public DefaultFileHandler(String fileName, boolean isAppend, int bufferSize, boolean useDirectMemory, Clock clock, ExceptionHandler handler) throws LoggerException {
        File file = new File(fileName);
        File parent = file.getParentFile();
        if (null != parent && !parent.exists()) {
            parent.mkdirs();
        }

        if (!isAppend) {
            file.delete();
        }

        long initialSize = isAppend ? file.length() : 0;
        long initialTime = file.exists() ? file.lastModified() : clock.currentTimeMillis();

        FileChannel fileChannel = null;
        try {
            fileChannel = new FileOutputStream(fileName).getChannel();
            fileChannel.position(initialSize);
        } catch (IOException e) {
            IOs.closeQuietly(fileChannel);
            throw new LoggerException(e);
        }
        this.fileName = fileName;
        this.isAppend = isAppend;
        this.fileChannel = fileChannel;
        if (useDirectMemory) {
            this.buffer = ByteBuffer.allocateDirect(bufferSize);
        } else {
            this.buffer = ByteBuffer.allocate(bufferSize);
        }
        this.size = initialSize;
        this.initialTime = initialTime;
        this.clock = clock;
        this.handler = handler;
    }

    @Override
    public synchronized void close() {
        flush();
        try {
            fileChannel.close();
        } catch (IOException e) {
            handler.handleException("Unable to close RandomAccessFile", e);
        }
    }

    @Override
    public final void write(byte[] data) {
        write(data, 0, data.length);
    }

    protected synchronized void write(byte[] bytes, int offset, int length) {
        size += length;
        int chunk;
        do {
            if (length > buffer.remaining()) {
                flush();
            }
            chunk = Math.min(length, buffer.remaining());
            buffer.put(bytes, offset, chunk);
            offset += chunk;
            length -= chunk;
        } while (length > 0);
    }

    @Override
    public synchronized void flush() {
        buffer.flip();
        try {
            fileChannel.write(buffer);
        } catch (IOException e) {
            handler.handleException("Error in flush buffer to randomAccessFile", e);
        }
        buffer.clear();
    }

    @Override
    public String getName() {
        return this.fileName;
    }

    @Override
    public long length() {
        return this.size;
    }

    @Override
    public long initialTime() {
        return this.initialTime;
    }
}
