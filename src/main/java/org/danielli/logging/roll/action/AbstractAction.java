package org.danielli.logging.roll.action;

import org.danielli.logging.exception.ExceptionHandler;

import java.io.IOException;

/**
 * 抽象轮转动作。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public abstract class AbstractAction implements Action {

    protected final ExceptionHandler handler;
    private boolean complete = false;
    private boolean interrupted = false;

    protected AbstractAction(ExceptionHandler handler) {
        this.handler = handler;
    }

    @Override
    public abstract boolean execute() throws IOException;

    @Override
    public synchronized void run() {
        if (!interrupted) {
            try {
                execute();
            } catch (IOException e) {
                handler.handleException(this.getClass().getName() + " execute error.", e);
            }
            complete = true;
            interrupted = true;
        }
    }

    @Override
    public synchronized void close() {
        interrupted = true;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }
}
