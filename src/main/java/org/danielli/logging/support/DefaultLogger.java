package org.danielli.logging.support;

import org.danielli.logging.handler.FileHandler;
import org.danielli.logging.Logger;
import org.danielli.logging.LoggerEvent;

/**
 * 默认日志文件写入器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class DefaultLogger<T extends FileHandler> implements Logger {

    protected final boolean immediateFlush;
    protected final T handler;

    public DefaultLogger(boolean immediateFlush, T handler) {
        this.immediateFlush = immediateFlush;
        this.handler = handler;
    }

    @Override
    public final void write(LoggerEvent event) {
        write(event, true);
    }

    @Override
    public void write(LoggerEvent event, boolean endOfBatch) {
        byte[] bytes = event.toByteArray();
        if (bytes.length > 0) {
            handler.write(bytes);
            if (this.immediateFlush || endOfBatch) {
                handler.flush();
            }
        }
    }

    @Override
    public void close() {
        handler.close();
    }
}
