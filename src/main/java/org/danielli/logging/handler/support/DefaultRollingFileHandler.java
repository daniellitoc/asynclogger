package org.danielli.logging.handler.support;

import org.danielli.common.clock.Clock;
import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.logging.handler.RollingFileHandler;
import org.danielli.logging.roll.Rollover;
import org.danielli.logging.roll.action.Action;
import org.danielli.logging.LoggerEvent;
import org.danielli.logging.roll.action.AbstractAction;
import org.danielli.logging.roll.pattern.FilePattern;
import org.danielli.logging.roll.trigger.Trigger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * 默认可轮转日志文件。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class DefaultRollingFileHandler extends DefaultFileHandler implements RollingFileHandler {

    protected final Semaphore semaphore = new Semaphore(1);

    protected final Trigger<RollingFileHandler> trigger;
    protected final Rollover rollover;
    protected final FilePattern filePattern;

    public DefaultRollingFileHandler(String fileName, FilePattern filePattern, boolean isAppend, int bufferSize, boolean useDirectMemory,
                                     Clock clock, Trigger<RollingFileHandler> trigger, Rollover rollover, ExceptionHandler handler) {
        super(fileName, isAppend, bufferSize, useDirectMemory, clock, handler);
        this.trigger = trigger;
        this.rollover = rollover;
        this.filePattern = filePattern;
    }

    @Override
    public void initialize() {
        trigger.initialize(this);
    }

    @Override
    public synchronized void checkRollover(LoggerEvent event) {
        if (trigger.isTriggeringEvent(event) && rolling()) {
            try {
                size = 0;
                initialTime = clock.currentTimeMillis();
                recreate();
            } catch (IOException e) {
                handler.handleException("Recreate RandomAccessFile error.", e);
            }
        }
    }

    protected void recreate() throws IOException {
        this.fileChannel = new FileOutputStream(fileName).getChannel();
        if (isAppend) {
            this.fileChannel.position(this.fileChannel.size());
        }
    }

    private boolean rolling() {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            handler.handleException("Thread interrupted when rolling", e);
            return false;
        }

        boolean success = false;
        Thread thread = null;

        try {
            Rollover.Description descriptor = rollover.rollover(this.fileName, this.filePattern);
            if (descriptor != null) {
                super.close();
                if (descriptor.getSync() != null) {
                    try {
                        success = descriptor.getSync().execute();
                    } catch (Exception e) {
                        handler.handleException("Error in synchronous task", e);
                    }
                }

                if (success && descriptor.getAsync() != null) {
                    // 没必要用线程池
                    thread = new Thread(new AsyncAction(descriptor.getAsync()));
                    thread.setDaemon(true);
                    thread.start();
                }
                return true;
            }
            return false;
        } finally {
            if (thread == null || !thread.isAlive()) {
                semaphore.release();
            }
        }
    }

    @Override
    public FilePattern getFilePattern() {
        return this.filePattern;
    }

    @Override
    public synchronized void close() {
        super.close();
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            handler.handleException("Thread interrupted when compress", e);
        } finally {
            semaphore.release();
        }
    }

    protected class AsyncAction extends AbstractAction {

        private final Action action;

        public AsyncAction(Action action) {
            super(DefaultRollingFileHandler.this.handler);
            this.action = action;
        }

        @Override
        public boolean execute() throws IOException {
            try {
                return action.execute();
            } finally {
                semaphore.release();
            }
        }

        @Override
        public void close() {
            action.close();
        }

        @Override
        public boolean isComplete() {
            return action.isComplete();
        }
    }
}
