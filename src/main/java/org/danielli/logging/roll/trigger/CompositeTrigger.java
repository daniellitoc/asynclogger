package org.danielli.logging.roll.trigger;

import org.danielli.logging.LoggerEvent;
import org.danielli.logging.handler.RollingFileHandler;

/**
 * 组合触发器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class CompositeTrigger<T extends RollingFileHandler> implements Trigger<T> {

    private final Trigger<T>[] triggers;

    @SafeVarargs
    public CompositeTrigger(Trigger<T>... triggers) {
        this.triggers = triggers;
    }

    @Override
    public void initialize(T handler) {
        for (Trigger<T> trigger : triggers) {
            trigger.initialize(handler);
        }
    }

    @Override
    public boolean isTriggeringEvent(LoggerEvent event) {
        for (Trigger<T> trigger : triggers) {
            if (trigger.isTriggeringEvent(event)) {
                return true;
            }
        }
        return false;
    }
}
