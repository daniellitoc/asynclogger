package org.danielli.logging.support;

import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import org.danielli.logging.Logger;
import org.danielli.logging.LoggerEvent;
import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.common.concurrent.async.InvokeFlusher;

/**
 * 异步Logger。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class AsyncLogger implements Logger {

    private final InvokeFlusher<LoggerEvent> flusher;
    private final Logger logger;
    private final ExceptionHandler handler;

    public AsyncLogger(Logger logger, WaitStrategy waitStrategy, ProducerType producerType, int bufferSize, int notifySize,
                       ExceptionHandler handler) {
        this.logger = logger;
        this.handler = handler;
        InvokeFlusher.Builder<LoggerEvent> builder = new InvokeFlusher.Builder<LoggerEvent>().setBufferSize(bufferSize).setNotifySize(notifySize)
                .setProducerType(producerType).setWaitStrategy(waitStrategy).setNamePrefix("asynclogger");
        builder.addListenerGroup(new LoggerEventListener());
        this.flusher = builder.build();

    }

    @Override
    public void write(LoggerEvent event) {
        this.flusher.add(event);
    }

    @Override
    public void write(LoggerEvent event, boolean endOfBatch) {
        if (endOfBatch) {
            syncWrite(event, true);
        } else {
            write(event);
        }
    }

    protected void syncWrite(LoggerEvent event, boolean endOfBatch) {
        this.logger.write(event, endOfBatch);
    }

    @Override
    public void close() {
        this.flusher.shutdown();
        this.logger.close();
    }

    public class LoggerEventListener implements InvokeFlusher.EventListener<LoggerEvent> {

        @Override
        public void onException(Throwable e, long sequence, LoggerEvent event) {
            AsyncLogger.this.handler.handleEventException(e.getMessage(), e, event);
        }

        @Override
        public void onEvent(LoggerEvent event, boolean endOfBatch) throws Exception {
            syncWrite(event, endOfBatch);
        }
    }
}
