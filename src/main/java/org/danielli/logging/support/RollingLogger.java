package org.danielli.logging.support;

import org.danielli.logging.LoggerEvent;
import org.danielli.logging.handler.RollingFileHandler;

/**
 * 可轮转日志文件写入器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class RollingLogger<T extends RollingFileHandler> extends DefaultLogger<T> {

    public RollingLogger(boolean immediateFlush, T handler) {
        super(immediateFlush, handler);
        handler.initialize();
    }

    @Override
    public void write(LoggerEvent event, boolean endOfBatch) {
        handler.checkRollover(event);
        super.write(event, endOfBatch);
    }
}
