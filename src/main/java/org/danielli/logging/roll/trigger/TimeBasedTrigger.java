package org.danielli.logging.roll.trigger;

import org.danielli.logging.LoggerEvent;
import org.danielli.logging.handler.RollingFileHandler;
import org.danielli.logging.roll.pattern.FilePattern;

/**
 * 基于时间触发器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class TimeBasedTrigger<T extends RollingFileHandler> implements Trigger<T> {

    private final int interval;
    private final boolean modulate;

    private FilePattern filePattern;
    private long nextRollover;
    private T handler;

    public TimeBasedTrigger(int interval, boolean modulate) {
        this.interval = interval;
        this.modulate = modulate;
    }

    @Override
    public void initialize(T handler) {
        this.handler = handler;
        this.filePattern = handler.getFilePattern();

        // 初始化nextFileTime
        filePattern.getNextTime(handler.initialTime(), interval, modulate);
        // 初始化prevFileTime
        nextRollover = filePattern.getNextTime(handler.initialTime(), interval, modulate);
    }

    @Override
    public boolean isTriggeringEvent(LoggerEvent event) {
        if (handler.length() == 0) {
            return false;
        }
        final long now = event.getTimeMillis();
        if (now > nextRollover) {
            nextRollover = filePattern.getNextTime(now, interval, modulate);
            return true;
        }
        return false;
    }
}
