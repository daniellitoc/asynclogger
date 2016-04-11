package org.danielli.logging.support;

import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import org.danielli.common.concurrent.async.InvokeFlusher;
import org.danielli.logging.Logger;
import org.danielli.logging.LoggerEvent;
import org.danielli.logging.exception.ExceptionHandler;

import java.util.concurrent.atomic.AtomicInteger;

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
    private final AddAction addAction;

    public AsyncLogger(Logger logger, WaitStrategy waitStrategy, ProducerType producerType, int bufferSize, int notifySize,
                       AddAction addAction, ExceptionHandler handler) {
        this.logger = logger;
        this.addAction = addAction;
        this.handler = handler;
        InvokeFlusher.Builder<LoggerEvent> builder = new InvokeFlusher.Builder<LoggerEvent>().setBufferSize(bufferSize).setNotifySize(notifySize)
                .setProducerType(producerType).setWaitStrategy(waitStrategy).setNamePrefix("asynclogger");
        builder.addListenerGroup(new LoggerEventListener());
        this.flusher = builder.build();

    }

    @Override
    public void write(LoggerEvent event) {
        addAction.add(flusher, event, handler);
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

    /**
     * 添加行为，用于控制刷新器添加日志策略（等待写入、尝试写入、丢弃日志等）。
     *
     * @author Daniel Li
     * @since 8 August 2015
     */
    public interface AddAction {

        void add(InvokeFlusher<LoggerEvent> flusher, LoggerEvent event, ExceptionHandler handler);

    }

    /**
     * 默认的添加行为。小于0情况下等待写入；等于0情况下只进行尝试写入；大于1场景，先进行尝试写入，写入失败后，进行等待写入，超过次数的进行丢弃。
     *
     * @author Daniel Li
     * @since 8 August 2015
     */
    public static class DefaultAddAction implements AddAction {

        private final int syncSize;
        private AtomicInteger syncPerm;

        public DefaultAddAction(int syncSize) {
            this.syncSize = syncSize;
            if (syncSize > 0) {
                syncPerm = new AtomicInteger(0);
            }
        }

        @Override
        public void add(InvokeFlusher<LoggerEvent> flusher, LoggerEvent event, ExceptionHandler handler) {
            if (syncSize < 0) {
                flusher.add(event);
                return;
            }
            if (syncSize == 0) {
                if (!flusher.tryAdd(event)) {
                    handler.handleEvent("discard", event);
                }
                return;
            }
            if (flusher.tryAdd(event)) {
                syncPerm.set(0);
            } else if (syncPerm.incrementAndGet() <= syncSize) {
                flusher.add(event);
            } else {
                handler.handleEvent("discard", event);
            }
        }
    }
}
