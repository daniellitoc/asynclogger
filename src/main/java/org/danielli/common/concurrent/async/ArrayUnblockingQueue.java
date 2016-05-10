package org.danielli.common.concurrent.async;

import com.lmax.disruptor.util.Util;
import sun.misc.Unsafe;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TODO completion javadoc.
 *
 * @author Daniel Li
 * @since 07 四月 2016
 */
public class ArrayUnblockingQueue<T> {

    protected static final long INITIAL_VALUE = -1L;
    protected final long indexMask;
    protected final String threadName;
    protected final int bufferSize;
    protected final Processor<T> processor;
    protected final Waitor waitor;
    protected final Elements elements;
    protected final ProducerSequence put = new ProducerSequence(INITIAL_VALUE);
    protected final ConsumerSequence get = new ConsumerSequence(INITIAL_VALUE);
    protected ProcessorThread thread;


    ArrayUnblockingQueue(String threadName, int bufferSize, Processor<T> processor, Waitor waitor) {
        this.threadName = threadName;
        this.bufferSize = bufferSize;
        this.elements = new Elements();
        this.processor = processor;
        this.waitor = waitor;
        this.indexMask = bufferSize - 1;
    }


    public void start() {
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }
        if (processor == null) {
            throw new IllegalArgumentException("processor must be not null");
        }
        if (waitor == null) {
            throw new IllegalArgumentException("waitor must be not null");
        }
        ProcessorThread thread = new ProcessorThread();
        thread.start();
        this.thread = thread;
    }

    // put + 1 - get <= bufferSize
    public void put(T data) {
        if (data == null) {
            return;
        }

        long current;
        long next;
        do {
            current = put.get();
            next = current + 1;
            if (next - bufferSize > get.get()) {
                LockSupport.parkNanos(1);
            } else if (put.compareAndSet(current, next)) {
                break;
            }
        } while (true);

        elements.elementAt(next).value = data;
        waitor.signal();
    }

    // put + 1 - get <= bufferSize
    public boolean tryPut(T data) {
        if (data == null) {
            return true;
        }

        long current;
        long next;
        do {
            current = put.get();
            next = current + 1;

            if (next - bufferSize > get.get()) {
                return false;
            }
        } while (!put.compareAndSet(current, next));
        elements.elementAt(next).value = data;
        waitor.signal();
        return true;
    }

    public void stop() {
        try {
            stop(0, TimeUnit.MICROSECONDS);
        } catch (TimeoutException e) {
        }
    }

    public void stop(long timeout, TimeUnit timeUnit) throws TimeoutException {
        try {
            long endTimeout = System.currentTimeMillis() + timeUnit.toMillis(timeout);

            try {
                thread.shutdown(timeout, timeUnit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                if (timeout == 0) {
                    Thread.sleep(2 * 1000);
                } else {
                    long waitTime = (endTimeout - System.currentTimeMillis()) / 2;
                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    }

                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (timeout > 0 && System.currentTimeMillis() > endTimeout) {
                throw new TimeoutException();
            }

            long putIndex = put.get();
            long getIndex = get.get();
            if (putIndex == getIndex) {
                return;
            }
            for (long i = getIndex; i < putIndex; i++) {
                Element<T> element = elements.elementAt(i);
                if (element.value != null) {
                    try {
                        processor.process(element.value);
                    } catch (Throwable e) {
                        processor.onThrowable(element.value, e);
                    } finally {
                        element.value = null;
                    }
                    if (timeout > 0 && System.currentTimeMillis() > endTimeout) {
                        throw new TimeoutException();
                    }
                }
            }
        } finally {
            put.set(INITIAL_VALUE);
            get.set(INITIAL_VALUE);
        }
    }

    public interface Processor<T> {

        void process(T data);

        void onTimeout(long current);

        void onThrowable(T data, Throwable e);

    }

    public interface Waitor {

        void signal();

        long wait(long next, ProducerSequence put, ProcessHandler handler) throws InterruptedException, TimeoutException;
    }

    public interface ProcessHandler {

        boolean isInterrupt();

    }

    private static class Element<T> {
        public volatile T value;
    }

    public static class BlockingWaitor implements Waitor {

        private ReentrantLock lock = new ReentrantLock();
        private Condition notifyCondition = lock.newCondition();
        private AtomicBoolean needSignal = new AtomicBoolean(false);

        @Override
        public void signal() {
            if (needSignal.getAndSet(false)) {
                lock.lock();
                try {
                    notifyCondition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public long wait(long next, ProducerSequence put, ProcessHandler handler) throws InterruptedException, TimeoutException {
            long available;
            if ((available = put.get()) < next) {
                lock.lock();
                try {
                    if ((available = put.get()) < next) {
                        needSignal.set(true);
                        if ((available = put.get()) < next && !handler.isInterrupt()) {
                            notifyCondition.await();
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
            return available;
        }
    }

    public static class TimeoutWaitor implements Waitor {

        private static TimeoutException exception = new TimeoutException();

        private final long timeout;
        private ReentrantLock lock = new ReentrantLock();
        private Condition notifyCondition = lock.newCondition();
        private AtomicBoolean needSignal = new AtomicBoolean(false);

        public TimeoutWaitor(long timeout, TimeUnit timeUnit) {
            this.timeout = timeUnit.toNanos(timeout);
        }

        @Override
        public void signal() {
            if (needSignal.getAndSet(false)) {
                lock.lock();
                try {
                    notifyCondition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public long wait(long next, ProducerSequence put, ProcessHandler handler) throws InterruptedException, TimeoutException {
            long nanos = timeout;
            long available;
            if ((available = put.get()) < next) {
                lock.lock();
                try {
                    if ((available = put.get()) < next) {
                        needSignal.set(true);
                        if ((available = put.get()) < next && !handler.isInterrupt()) {
                            nanos = notifyCondition.awaitNanos(nanos);
                            if (nanos <= 0) {
                                throw exception;
                            }
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
            return available;
        }
    }

    public static class SleepingWaitor implements Waitor {

        private final int retries;

        public SleepingWaitor(int retries) {
            this.retries = retries;
        }

        @Override
        public void signal() {
        }

        @Override
        public long wait(long next, ProducerSequence put, ProcessHandler handler) throws InterruptedException, TimeoutException {
            long available;
            int counter = retries;
            while ((available = put.get()) < next && !handler.isInterrupt()) {
                counter = applyWaitMethod(counter);
            }
            return available;
        }

        private int applyWaitMethod(int counter) {
            if (counter > 100) {
                --counter;
            } else if (counter > 0) {
                --counter;
                Thread.yield();
            } else {
                LockSupport.parkNanos(1L);
            }
            return counter;
        }
    }

    private class Elements {
        protected long p1, p2, p3, p4, p5, p6, p7;
        protected final Element<T>[] elements;
        protected int p15;
        protected long p8, p9, p10, p11, p12, p13, p14;

        public Elements() {
            elements = new Element[bufferSize];
            for (int i = 0; i < bufferSize; i++) {
                elements[i] = new Element<>();
            }
        }

        @SuppressWarnings("unchecked")
        protected final Element<T> elementAt(long sequence) {
            return elements[(int) (sequence & indexMask)];
        }
    }

    private class ProcessorThread extends Thread implements ProcessHandler {

        private volatile boolean running = true;
        private volatile boolean interrupt;

        public ProcessorThread() {
            super(threadName);
        }

        public void shutdown(long timeout, TimeUnit timeUnit) throws TimeoutException, InterruptedException {
            this.running = false;
            interrupt();
            join(timeUnit.toMillis(timeout));
            if (isAlive()) {
                throw new TimeoutException();
            }
        }

        @Override
        public void run() {
            long next = get.get() + 1L;
            T value = null;
            while (running) {
                try {
                    // get + batch <= put
                    long available = waitor.wait(next, put, this);
                    while (next <= available) {
                        Element<T> element = elements.elementAt(next);
                        processor.process(value = element.value);
                        next++;
                    }
                    get.set(available);
                } catch (TimeoutException e) {
                    processor.onTimeout(next - 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable e) {
                    processor.onThrowable(value, e);
                    next++;
                }
            }
        }

        @Override
        public void interrupt() {
            interrupt = true;
            super.interrupt();
        }

        @Override
        public boolean isInterrupt() {
            return interrupt;
        }
    }
}

class LPADDING {
    protected long p1, p2, p3, p4, p5, p6, p7;
}

class VALUE extends LPADDING {
    protected volatile long value;
}

class RPADDING extends VALUE {
    protected long p9, p10, p11, p12, p13, p14, p15;
}

class ProducerSequence extends RPADDING {

    private static final Unsafe UNSAFE;
    private static final long VALUE_OFFSET;

    static {
        UNSAFE = Util.getUnsafe();
        try {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(VALUE.class.getDeclaredField("value"));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ProducerSequence(long initialValue) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, initialValue);
    }

    public long get() {
        return value;
    }

    public void set(long value) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, value);
    }

    public void setVolatile(long value) {
        UNSAFE.putLongVolatile(this, VALUE_OFFSET, value);
    }

    public boolean compareAndSet(long expectedValue, long newValue) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expectedValue, newValue);
    }

    public long incrementAndGet() {
        return addAndGet(1L);
    }

    public long addAndGet(final long increment) {
        long currentValue;
        long newValue;

        do {
            currentValue = get();
            newValue = currentValue + increment;
        }
        while (!compareAndSet(currentValue, newValue));

        return newValue;
    }
}

class ConsumerSequence extends RPADDING {

    public ConsumerSequence(long initialValue) {
        this.value = initialValue;
    }

    public long get() {
        return value;
    }

    public void set(long value) {
        this.value = value;
    }
}
