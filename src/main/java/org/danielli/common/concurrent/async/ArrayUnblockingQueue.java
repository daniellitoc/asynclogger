package org.danielli.common.concurrent.async;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

    private static final long INIT_SQEUENCE = -1;
    private final String threadName;
    private final int bufferSize;
    private final int indexMask;
    private final Processor<T> processor;
    private final Waitor waitor;
    private volatile Element<T>[] elements;
    private ProcessorThread thread;
    private AtomicLong putSequence = new AtomicLong(INIT_SQEUENCE);
    private volatile long getSequence = INIT_SQEUENCE;

    public ArrayUnblockingQueue(String threadName, int bufferSize, Processor<T> processor, Waitor waitor) {
        this.threadName = threadName;
        this.bufferSize = bufferSize;
        this.indexMask = bufferSize - 1;
        this.processor = processor;
        this.waitor = waitor;
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
        elements = new Element[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            elements[i] = new Element<>();
        }
        thread = new ProcessorThread();
        thread.start();
    }

    // put + 1 - get <= bufferSize
    public void put(T data) {
        if (data == null) {
            return;
        }

        long next;
        do {
            long current = putSequence.get();
            next = current + 1;
            if (next - bufferSize > getSequence) {
                LockSupport.parkNanos(1);
            } else if (putSequence.compareAndSet(current, next)) {
                break;
            }
        } while (true);

        try {
            index(next).value = data;
        } catch (NullPointerException e) {
            processor.onThrowable(data, new IllegalStateException("closed"));
            putSequence.decrementAndGet();
        }
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
            current = putSequence.get();
            next = current + 1;
            if (next - bufferSize > getSequence) {
                return false;
            }
        }
        while (!putSequence.compareAndSet(current, next));

        try {
            index(next).value = data;
        } catch (NullPointerException e) {
            putSequence.decrementAndGet();
            return false;
        }

        waitor.signal();
        return true;
    }

    private Element<T> index(long sequcnce) {
        return elements[(int) (sequcnce & indexMask)];
    }

    public void stop() {
        try {
            stop(-1, TimeUnit.MICROSECONDS);
        } catch (TimeoutException e) {
        }
    }

    public void stop(long timeout, TimeUnit timeUnit) throws TimeoutException {
        long endTimeout = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        thread.interrupt();
        while (thread.isRunning()) {
            if (timeout > 0 && System.currentTimeMillis() > endTimeout) {
                throw new TimeoutException();
            }
        }
        Element<T>[] tmp = elements;
        elements = null;

        System.out.println(putSequence);
        System.out.println(getSequence);
        while (true) {
            long putIndex = putSequence.get();
            long getIndex = getSequence;
            if (putIndex == getIndex) {
                break;
            }
            for (long i = getIndex; i < putIndex; i++) {
                Element<T> element = tmp[(int) (i & indexMask)];
                if (element.value != null) {
                    System.out.println(element.value);
                    try {
                        processor.process(element.value);
                    } catch (Throwable e) {
                        processor.onThrowable(element.value, e);
                    } finally {
                        tmp[(int) (i & indexMask)] = null;
                        putSequence.decrementAndGet();
                    }
                    if (timeout > 0 && System.currentTimeMillis() > endTimeout) {
                        throw new TimeoutException();
                    }
                }
            }
        }
    }

    public interface Processor<T> {

        void process(T data);

        void onTimeout(long current);

        void onThrowable(T data, Throwable e);

    }

    public interface Waitor {

        void signal();

        long wait(long next, AtomicLong put, ProcessHandler handler) throws InterruptedException, TimeoutException;
    }

    public interface ProcessHandler {

        boolean isInterrupt();

        boolean isRunning();
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
        public long wait(long next, AtomicLong put, ProcessHandler handler) throws InterruptedException, TimeoutException {
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
        public long wait(long next, AtomicLong put, ProcessHandler handler) throws InterruptedException, TimeoutException {
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
        public long wait(long next, AtomicLong put, ProcessHandler handler) throws InterruptedException, TimeoutException {
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

    private static class Element<T> {
        public volatile T value;
    }

    private class ProcessorThread extends Thread implements ProcessHandler {

        private volatile boolean running;
        private volatile boolean interrupt;

        public ProcessorThread() {
            super(threadName);
        }

        @Override
        public synchronized void start() {
            super.start();
            running = true;
        }

        @Override
        public void run() {
            long next = getSequence + 1L;
            T value = null;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // get + batch <= put
                    long available = waitor.wait(next, putSequence, this);
                    while (next <= available) {
                        Element<T> element = index(next);
                        try {
                            processor.process(value = element.value);
                            next++;
                        } finally {
                            element.value = null;
                        }
                    }
                    getSequence = available;
                } catch (TimeoutException e) {
                    processor.onTimeout(next - 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable e) {
                    processor.onThrowable(value, e);
                    next++;
                }
            }
            running = false;
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

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
