package org.danielli.common.concurrent.async;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.danielli.common.batch.BatchForwarder;
import org.danielli.common.clock.Clock;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 异步刷新器，生产者/消费者模式（无锁队列），同一事件只被一个消费者消费。多消费者并行。单挑”通知“模式。
 * <pre>
 * 策略选择：性能从高 - 低
 *  BusySpinWaitStrategy: 自旋等待，延迟最低，资源占用相对最多；用于处理线程小于物理内核(禁用超线程)
 *  YieldingWaitStrategy: 忙循环等待，默认前100次只检测，之后让出CPU，等待下次调度；低延迟，平衡了资源占用；需要高性能和处理线程小于逻辑内核(开启超线程)推荐使用。
 *  SleepingWaitStrategy: 忙循环等待，默认前100次只检测，后100次让出CPU，等待下次调度，之后每次睡1纳秒；平衡了使用率，延迟不均衡。
 *  BlockingWaitStrategy: 锁和条件；CPU使用率最低，延迟最大。
 * </pre>
 * <pre>
 * 生产者类型：
 *  SINGLE: 但生产者。
 *  MULTI: 多生产者。
 * </pre>
 * <pre>
 * 事件监听器：
 *  EventListener：接口，提供处理单个实体。
 *  BatchEventListener：抽象类，EventListener实现，提供聚合为批量。
 * </pre>
 *
 * @param <E> 实体。
 * @author Daniel Li
 * @since 8 August 2015
 */
public class ParallelFlusher<E> {

    private final Disruptor<Holder> disruptor;
    private final EventListener<E> eventListener;
    private final EventTranslatorOneArg<Holder, E> eventTranslator;
    private final ExecutorService executorService;
    private volatile RingBuffer<Holder> ringBuffer;

    private ParallelFlusher(Builder<E> builder) {
        this.executorService = Executors.newFixedThreadPool(builder.threads, new ThreadFactoryBuilder().
                setNameFormat("parallelflusher-" + builder.namePrefix + "-pool-%d").build());

        EventFactory<Holder> eventFactory = new HolderEventFactory();

        ExceptionHandler exceptionHandler = new HolderExceptionHandler();

        this.eventListener = builder.listener;
        this.eventTranslator = new HolderEventTranslator();

        int bufferSize = builder.bufferSize;
        ProducerType producerType = builder.producerType;
        WaitStrategy waitStrategy = builder.waitStrategy;
        Disruptor<Holder> disruptor = new Disruptor<>(eventFactory, bufferSize, executorService, producerType, waitStrategy);
        disruptor.handleExceptionsWith(exceptionHandler);

        @SuppressWarnings("unchecked") WorkHandler<Holder>[] workHandlers = new WorkHandler[builder.threads];
        for (int i = 0, length = workHandlers.length; i < length; i++) {
            workHandlers[i] = new HolderWorkHandler();
        }
        //noinspection unchecked
        disruptor.handleEventsWithWorkerPool(workHandlers);

        this.ringBuffer = disruptor.start();
        this.disruptor = disruptor;
    }

    private static boolean hasAvailableCapacity(RingBuffer<?> ringBuffer) {
        return !ringBuffer.hasAvailableCapacity(ringBuffer.getBufferSize());
    }

    @SafeVarargs
    private static <E> void process(EventListener<E> listener, Throwable e, E... events) {
        for (E event : events) {
            process(listener, e, event);
        }
    }

    private static <E> void process(EventListener<E> listener, Throwable e, List<E> events) {
        for (E event : events) {
            process(listener, e, event);
        }
    }

    private static <E> void process(EventListener<E> listener, Throwable e, E event) {
        listener.onException(e, -1, event);
    }

    public void add(E event) {
        RingBuffer<Holder> temp = ringBuffer;
        if (temp == null) {
            process(this.eventListener, new IllegalStateException("distruptor is closed."), event);
            return;
        }

        // 关闭后回产生NPE
        try {
            // 不使用临时变量，确保及时设置null
            ringBuffer.publishEvent(eventTranslator, event);
        } catch (NullPointerException e) {
            process(this.eventListener, new IllegalStateException("distruptor is closed.", e), event);
        }
    }

    @SafeVarargs
    public final void add(E... events) {
        RingBuffer<Holder> temp = ringBuffer;
        if (temp == null) {
            process(this.eventListener, new IllegalStateException("distruptor is closed."), events);
            return;
        }

        // 关闭后回产生NPE
        try {
            // 不使用临时变量，确保及时设置null
            ringBuffer.publishEvents(eventTranslator, events);
        } catch (NullPointerException e) {
            process(this.eventListener, new IllegalStateException("distruptor is closed.", e), events);
        }
    }

    public boolean tryAdd(E event) {
        RingBuffer<Holder> temp = ringBuffer;
        if (temp == null) {
            return false;
        }

        // 关闭后会产生NPE
        try {
            // 不使用临时变量，确保及时设置null
            return ringBuffer.tryPublishEvent(eventTranslator, event);
        } catch (NullPointerException e) {
            return false;
        }
    }

    @SafeVarargs
    public final boolean tryAdd(E... events) {
        RingBuffer<Holder> temp = ringBuffer;
        if (temp == null) {
            return false;
        }

        // 关闭后会产生NPE
        try {
            // 不使用临时变量，确保及时设置null
            return ringBuffer.tryPublishEvents(eventTranslator, events);
        } catch (NullPointerException e) {
            return false;
        }
    }

    public boolean isShutdown() {
        return ringBuffer != null;
    }

    public void shutdown() {
        RingBuffer<Holder> temp = ringBuffer;
        ringBuffer = null;
        if (temp == null) {
            return;
        }

        for (int i = 0; hasAvailableCapacity(temp) && i < 200; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
        }
        disruptor.shutdown();

        executorService.shutdown();
        try {
            executorService.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }


    public interface EventListener<E> {

        void onException(Throwable e, long sequence, E event);

        void onEvent(E event) throws Exception;
    }

    public static abstract class BatchEventListener<E> implements EventListener<E>, BatchForwarder.Processor<E> {

        private final BatchForwarder<E> forwarder;

        public BatchEventListener(int batchSize, int duration, TimeUnit timeUnit, Clock clock) {
            forwarder = new BatchForwarder<>(batchSize, duration, timeUnit, clock, this);
        }

        public abstract void onEvents(List<E> events) throws Exception;

        @Override
        public void process(List<E> events) {
            try {
                onEvents(events);
            } catch (Throwable e) {
                ParallelFlusher.process(this, e, events);
            }
        }

        @Override
        public final void onEvent(E event) throws Exception {
            forwarder.add(event);
        }
    }

    public static class Builder<E> {
        private ProducerType producerType = ProducerType.MULTI;
        private int bufferSize = 256 * 1024;
        private int threads = 1;
        private String namePrefix = "";
        private WaitStrategy waitStrategy = new YieldingWaitStrategy();
        private EventListener<E> listener;

        public Builder<E> setThreads(int threads) {
            Preconditions.checkArgument(threads > 0);
            this.threads = threads;
            return this;
        }

        public Builder<E> setListener(EventListener<E> listener) {
            this.listener = Preconditions.checkNotNull(listener);
            return this;
        }

        public Builder<E> setNamePrefix(String namePrefix) {
            this.namePrefix = Preconditions.checkNotNull(namePrefix);
            return this;
        }

        public Builder<E> setWaitStrategy(WaitStrategy waitStrategy) {
            this.waitStrategy = Preconditions.checkNotNull(waitStrategy);
            return this;
        }

        public Builder<E> setProducerType(ProducerType producerType) {
            this.producerType = Preconditions.checkNotNull(producerType);
            return this;
        }

        public Builder<E> setBufferSize(int bufferSize) {
            Preconditions.checkArgument(bufferSize > 0);
            this.bufferSize = bufferSize;
            return this;
        }

        public ParallelFlusher<E> build() {
            Preconditions.checkNotNull(listener);
            return new ParallelFlusher<>(this);
        }
    }

    private class Holder {

        private E event;

        public void setValue(E event) {
            this.event = event;
        }

    }

    private class HolderEventFactory implements EventFactory<Holder> {

        @Override
        public Holder newInstance() {
            return new Holder();
        }
    }

    private class HolderEventTranslator implements EventTranslatorOneArg<Holder, E> {

        @Override
        public void translateTo(Holder event, long sequence, E arg0) {
            event.setValue(arg0);
        }

    }

    private class HolderWorkHandler implements WorkHandler<Holder> {

        @Override
        public void onEvent(Holder event) throws Exception {
            eventListener.onEvent(event.event);
            event.setValue(null);
        }
    }

    private class HolderExceptionHandler implements ExceptionHandler {

        @Override
        public void handleEventException(Throwable ex, long sequence, Object event) {
            @SuppressWarnings("unchecked") Holder holder = (Holder) event;
            eventListener.onException(ex, sequence, holder.event);
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            throw new UnsupportedOperationException(ex);
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

}
