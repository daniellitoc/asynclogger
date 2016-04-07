package org.danielli.logging.roll.trigger;

import org.danielli.logging.LoggerEvent;
import org.danielli.logging.handler.RollingFileHandler;

/**
 * 触发器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public interface Trigger<T extends RollingFileHandler> {

    void initialize(T handler);

    boolean isTriggeringEvent(LoggerEvent event);
}
