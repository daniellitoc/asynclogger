package org.danielli.common.concurrent.async;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link org.danielli.common.concurrent.async.ParallelFlusher} 测试。
 *
 * @author Daniel Li
 * @since 22 August 2015
 */
public class ParallelFlusherTest {

    @Test
    public void test1() {
        final AtomicInteger value = new AtomicInteger();
        ParallelFlusher<Integer> parallelFlusher = new ParallelFlusher.Builder<Integer>().setBufferSize(1024).setNamePrefix("test").setThreads(2).setListener(new ParallelFlusher.EventListener<Integer>() {

            @Override
            public void onException(Throwable e, long sequence, Integer event) {
                e.printStackTrace();
                System.out.println(event);
            }

            @Override
            public void onEvent(Integer event) throws Exception {
                System.out.println(Thread.currentThread() + "\tOK: " + event + "\t" + value.incrementAndGet());
            }
        }).build();

        final AtomicInteger test = new AtomicInteger();
        for (int i = 0; i < 1000; i++) {
            parallelFlusher.add(test.incrementAndGet());
        }
        parallelFlusher.shutdown();
    }
}
