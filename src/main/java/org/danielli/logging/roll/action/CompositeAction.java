package org.danielli.logging.roll.action;

import org.danielli.logging.exception.ExceptionHandler;

import java.io.IOException;
import java.util.List;

/**
 * 轮转时组合行为。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class CompositeAction extends AbstractAction {

    private final Action[] actions;

    private final boolean stopOnError;

    public CompositeAction(ExceptionHandler handler, List<Action> actions, boolean stopOnError) {
        super(handler);
        this.actions = actions.toArray(new Action[actions.size()]);
        this.stopOnError = stopOnError;
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (IOException ex) {
            handler.handleException("Exception during file rollover.", ex);
        }
    }

    @Override
    public boolean execute() throws IOException {
        if (stopOnError) {
            for (Action action : actions) {
                if (!action.execute()) {
                    return false;
                }
            }
            return true;
        }

        boolean status = true;
        IOException exception = null;

        for (Action action : actions) {
            try {
                status &= action.execute();
            } catch (IOException ex) {
                status = false;
                if (exception == null) {
                    exception = ex;
                }
            }
        }

        if (exception != null) {
            throw exception;
        }

        return status;
    }
}