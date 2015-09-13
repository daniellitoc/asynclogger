package org.danielli.common.concurrent.async;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.EventHandlerGroup;
import com.lmax.disruptor.dsl.ProducerType;
import org.danielli.common.batch.BatchForwarder;
import org.danielli.common.clock.Clock;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 异步刷新器，生产者/消费者模式（无锁队列）。同一事件被所有消费者消费，多消费者可控制消费顺序。批量”通知“模式。
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
public class InvokeFlusher<E> {

    private final Disruptor<Holder> disruptor;
    private final List<EventListener<E>[]> listenerGroups;
    private final EventTranslatorOneArg<Holder, E> eventTranslator;
    private final ExecutorService executorService;
    private volatile RingBuffer<Holder> ringBuffer;

    private InvokeFlusher(Builder<E> builder) {
        this.executorService = Executors.newFixedThreadPool(builder.getThreads(), new ThreadFactoryBuilder().
                setNameFormat("invokeflusher-" + builder.namePrefix + "-pool-%d").build());

        EventFactory<Holder> eventFactory = new HolderEventFactory();

        this.listenerGroups = builder.listenerGroups;
        this.eventTranslator = new HolderEventTranslator();

        int bufferSize = builder.bufferSize;
        ProducerType producerType = builder.producerType;
        WaitStrategy waitStrategy = builder.waitStrategy;
        Disruptor<Holder> disruptor = new Disruptor<>(eventFactory, bufferSize, executorService, producerType, waitStrategy);
        disruptor.handleExceptionsWith(new HolderExceptionHandler());

        final int notifySize = builder.notifySize;
        List<EventHandler<Holder>[]> handlerGroups = Lists.transform(listenerGroups,
                new Function<EventListener<E>[], EventHandler<Holder>[]>() {
                    @Override
                    public EventHandler<Holder>[] apply(EventListener<E>[] input) {
                        EventHandler<Holder>[] result = new EventHandler[input.length];
                        for (int i = 0, length = input.length; i < length; i++) {
                            result[i] = new HolderEventHandler(input[i], notifySize);
                        }
                        return result;
                    }
                });

        EventHandlerGroup<Holder> handlerGroup = disruptor.handleEventsWith(handlerGroups.get(0));
        for (int i = 1, length = listenerGroups.size(); i < length; i++) {
            handlerGroup = handlerGroup.then(handlerGroups.get(i));
        }

        this.ringBuffer = disruptor.start();
        this.disruptor = disruptor;
    }

    private static boolean hasAvailableCapacity(RingBuffer<?> ringBuffer) {
        return !ringBuffer.hasAvailableCapacity(ringBuffer.getBufferSize());
    }

    private static <E> void process(List<EventListener<E>[]> listenerGroups, Throwable e, E... events) {
        for (E event : events) {
            process(listenerGroups, e, event);
        }
    }

    private static <E> void process(List<EventListener<E>[]> listenerGroups, Throwable e, E event) {
        for (EventListener<E>[] listeners : listenerGroups) {
            for (EventListener<E> listener : listeners) {
                listener.onException(e, -1, event);
            }
        }
    }

    private static <E> void process(EventListener<E> listener, Throwable e, List<E> events) {
        for (E element : events) {
            listener.onException(e, -1, element);
        }
    }

    public void add(E event) {
        RingBuffer<Holder> temp = ringBuffer;
        if (temp == null) {
            process(this.listenerGroups, new IllegalStateException("distruptor is closed."), event);
            return;
        }

        // 关闭后回产生NPE
        try {
            // 不使用临时变量，确保及时设置null
            ringBuffer.publishEvent(eventTranslator, event);
        } catch (NullPointerException e) {
            process(this.listenerGroups, new IllegalStateException("distruptor is closed.", e), event);
        }
    }

    public void add(E... events) {
        RingBuffer<Holder> temp = ringBuffer;
        if (temp == null) {
            process(this.listenerGroups, new IllegalStateException("distruptor is closed."), events);
            return;
        }

        // 关闭后回产生NPE
        try {
            // 不使用临时变量，确保及时设置null
            ringBuffer.publishEvents(eventTranslator, events);
        } catch (NullPointerException e) {
            process(this.listenerGroups, new IllegalStateException("distruptor is closed.", e), events);
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

    public boolean tryAdd(E... events) {
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

        void onEvent(E event, boolean endOfBatch) throws Exception;
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
            } catch (Exception e) {
                InvokeFlusher.process(this, e, events);
            }
        }

        @Override
        public final void onEvent(E event, boolean endOfBatch) throws Exception {
            forwarder.add(event, endOfBatch);
        }
    }

    public static class Builder<E> {
        private ProducerType producerType = ProducerType.MULTI;
        private int bufferSize = 256 * 1024;
        private int notifySize = 50;
        private String namePrefix = "";
        private WaitStrategy waitStrategy = new YieldingWaitStrategy();
        private List<EventListener<E>[]> listenerGroups = Lists.newArrayList();

        public Builder<E> setListenerGroups(List<EventListener<E>[]> listenerGroups) {
            this.listenerGroups = Preconditions.checkNotNull(listenerGroups);
            Preconditions.checkArgument(!listenerGroups.isEmpty());
            return this;
        }

        public Builder<E> addListenerGroup(EventListener<E>... listenerGroup) {
            this.listenerGroups.add(Preconditions.checkNotNull(listenerGroup));
            Preconditions.checkArgument(listenerGroup.length != 0);
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

        public Builder<E> setNotifySize(int notifySize) {
            Preconditions.checkArgument(notifySize > 0);
            this.notifySize = notifySize;
            return this;
        }

        private int getThreads() {
            int i = 0;
            for (EventListener<E>[] listenerGroup : listenerGroups) {
                i += listenerGroup.length;
            }
            return i;
        }

        public InvokeFlusher<E> build() {
            Preconditions.checkArgument(!listenerGroups.isEmpty());
            return new InvokeFlusher<>(this);
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

    private class HolderEventHandler implements SequenceReportingEventHandler<Holder> {

        private final int notifySize;
        private final EventListener<E> listener;
        private Sequence sequence;
        private int counter;

        public HolderEventHandler(EventListener<E> listener, int notifySize) {
            this.listener = listener;
            this.notifySize = notifySize;
        }

        @Override
        public void setSequenceCallback(Sequence sequence) {
            this.sequence = sequence;
        }

        @Override
        public void onEvent(Holder event, long sequence, boolean endOfBatch) throws Exception {
            try {
                listener.onEvent(event.event, endOfBatch);

                if (++counter > notifySize) {
                    this.sequence.set(sequence);
                    counter = 0;
                }
            } catch (Throwable e) {
                listener.onException(e, sequence, event.event);
                this.sequence.set(sequence);
            }
        }
    }

    private class HolderExceptionHandler implements ExceptionHandler {

        @Override
        public void handleEventException(Throwable ex, long sequence, Object event) {
            Holder holder = (Holder) event;
            throw new UnsupportedOperationException("Sequence: " + sequence + ". Event: " + (holder == null ? null : holder.event), ex);
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
