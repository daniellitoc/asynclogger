package org.danielli.logging.support;

import org.danielli.logging.Logger;
import org.danielli.logging.LoggerEvent;

/**
 * 支持过滤器的日志。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class FilterableLogger implements Logger {

    private final Logger logger;
    private final Filter[] filters;

    public FilterableLogger(Logger logger, Filter[] filters) {
        this.logger = logger;
        this.filters = filters;
    }

    @Override
    public void write(LoggerEvent event) {
        if (isWrite(event)) {
            logger.write(event);
        }
    }

    private boolean isWrite(LoggerEvent event) {
        for (Filter filter : filters) {
            if (!filter.isWrite(event)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void write(LoggerEvent event, boolean endOfBatch) {
        if (isWrite(event)) {
            logger.write(event, endOfBatch);
        }
    }

    @Override
    public void close() {
        logger.close();
    }
}
