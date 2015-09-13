package org.danielli.common.concurrent.async;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link org.danielli.common.concurrent.async.InvokeFlusher} 测试。
 *
 * @author Daniel Li
 * @since 22 August 2015
 */
public class InvokeFlusherTest {

    @Test
    public void test1() {
        final AtomicInteger value = new AtomicInteger();
        InvokeFlusher.Builder<Integer> builder = new InvokeFlusher.Builder<Integer>().setBufferSize(1024).setNotifySize(128).setNamePrefix("test");

        InvokeFlusher.EventListener<Integer>[] group1 = new InvokeFlusher.EventListener[1];
        group1[0] = new InvokeFlusher.EventListener<Integer>() {
            @Override
            public void onException(Throwable e, long sequence, Integer event) {
                e.printStackTrace();
                System.out.println(event);
            }

            @Override
            public void onEvent(Integer event, boolean endOfBatch) throws Exception {
                System.out.println(Thread.currentThread() + "\t1: " + event + "\t" + value.incrementAndGet() + "\tendOfBatch: " + endOfBatch);
            }
        };

        InvokeFlusher.EventListener<Integer>[] group2 = new InvokeFlusher.EventListener[2];
        group2[0] = new InvokeFlusher.EventListener<Integer>() {
            @Override
            public void onException(Throwable e, long sequence, Integer event) {
                e.printStackTrace();
                System.out.println(event);
            }

            @Override
            public void onEvent(Integer event, boolean endOfBatch) throws Exception {
                System.out.println(Thread.currentThread() + "\t2: " + event + "\t" + value.incrementAndGet() + "\tendOfBatch: " + endOfBatch);
            }
        };
        group2[1] = new InvokeFlusher.EventListener<Integer>() {
            @Override
            public void onException(Throwable e, long sequence, Integer event) {
                e.printStackTrace();
                System.out.println(event);
            }

            @Override
            public void onEvent(Integer event, boolean endOfBatch) throws Exception {
                System.out.println(Thread.currentThread() + "\t3: " + event + "\t" + value.incrementAndGet() + "\tendOfBatch: " + endOfBatch);
            }
        };

        builder.addListenerGroup(group1).addListenerGroup(group2);

        InvokeFlusher<Integer> flusher = builder.build();
        final AtomicInteger test = new AtomicInteger();
        for (int i = 0; i < 1; i++) {
            flusher.add(test.incrementAndGet());
        }
        flusher.shutdown();
    }
}
