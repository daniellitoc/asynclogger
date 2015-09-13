package org.danielli.common.batch;

import com.google.common.collect.Lists;
import org.danielli.common.clock.Clock;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 批量转发器（同步聚合单个元素到批量）。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class BatchForwarder<E> {

    protected final int batchSize;
    protected final long duration;
    protected final Clock clock;
    protected final Processor<E> processor;

    protected final List<E> events;

    protected volatile long batchEndTime;

    public BatchForwarder(int batchSize, long duration, TimeUnit timeUnit, Clock clock, Processor<E> processor) {
        this.events = Lists.newArrayListWithCapacity(batchSize);
        this.batchSize = batchSize;
        this.duration = timeUnit.toMillis(duration);
        this.clock = clock;
        this.processor = processor;

        this.batchEndTime = clock.currentTimeMillis() + this.duration;
    }

    public void add(E event) {
        add(event, false);
    }

    public void add(E event, boolean force) {
        doAdd(event);
        if (force || forward()) {
            processor.process(events);
            reset();
        }
    }

    protected void doAdd(E event) {
        this.events.add(event);
    }

    protected void reset() {
        this.events.clear();
        this.batchEndTime = this.clock.currentTimeMillis() + this.duration;
    }

    protected boolean forward() {
        return this.events.size() > this.batchSize || this.clock.currentTimeMillis() > this.batchEndTime;
    }

    public interface Processor<E> {

        void process(List<E> events);

    }
}
