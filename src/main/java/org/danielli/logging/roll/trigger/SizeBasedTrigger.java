package org.danielli.logging.roll.trigger;

import org.danielli.logging.LoggerEvent;
import org.danielli.logging.handler.RollingFileHandler;
import org.danielli.logging.roll.pattern.FilePattern;

/**
 * 基于大小触发器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class SizeBasedTrigger<T extends RollingFileHandler> implements Trigger<T> {

    private final long maxFileSize;
    private T handler;
    private FilePattern filePattern;

    public SizeBasedTrigger(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    @Override
    public void initialize(T handler) {
        this.handler = handler;
        this.filePattern = handler.getFilePattern();
    }

    @Override
    public boolean isTriggeringEvent(LoggerEvent event) {
        boolean trigger = handler.length() > maxFileSize;
        if (trigger) {
            filePattern.updateTime();
        }
        return trigger;
    }
}
