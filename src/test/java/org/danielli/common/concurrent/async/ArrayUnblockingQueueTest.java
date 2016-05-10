package org.danielli.common.concurrent.async;

import com.lmax.disruptor.SleepingWaitStrategy;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


/**
 * TODO completion javadoc.
 *
 * @author Daniel Li
 * @since 15 四月 2016
 */
public class ArrayUnblockingQueueTest {

    @Test
    public void test() throws Exception {

        final AtomicLong result = new AtomicLong();
        final ArrayUnblockingQueue<Object> queue2 = new ArrayUnblockingQueue<>("thread", 1024, new ArrayUnblockingQueue.Processor<Object>() {
            @Override
            public void process(Object data) {
                result.incrementAndGet();
            }

            @Override
            public void onTimeout(long current) {
                System.out.println("timeout: " + current);
            }

            @Override
            public void onThrowable(Object data, Throwable e) {
                e.printStackTrace();
            }
        }, new ArrayUnblockingQueue.SleepingWaitor(200));

        queue2.start();
        new PerformanceChecker().execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 1024 * 3; i++) {
                    queue2.put(i);
                }
            }
        }, "a", 4, 2000);
        Thread.sleep(6 * 1000);
        queue2.stop();
        System.out.println(result.get());
//
//        final ArrayBlockingQueue<Object> queue1 = new ArrayBlockingQueue<Object>(1024 * 16 * 16);
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true) {
//                    queue1.poll();
//                }
//            }
//        }).start();
//
//        new PerformanceChecker().execute(new Runnable() {
//            @Override
//            public void run() {
//                for (int i = 0; i < 10240000 * 3; i++) {
//                    try {
//                        queue1.put(i);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }, "a", 4, 2000);
//        Thread.sleep(3 * 1000);
//
//        InvokeFlusher.Builder<Integer> builder = new InvokeFlusher.Builder<Integer>().setBufferSize(1024 * 16 * 16).setNotifySize(128).setNamePrefix("test");
//
//        InvokeFlusher.EventListener<Integer>[] group1 = new InvokeFlusher.EventListener[1];
//        group1[0] = new InvokeFlusher.EventListener<Integer>() {
//            @Override
//            public void onException(Throwable e, long sequence, Integer event) {
//
//            }
//
//            @Override
//            public void onEvent(Integer event, boolean endOfBatch) throws Exception {
//
//            }
//        };
//        builder.addListenerGroup(group1);
//
//        final InvokeFlusher<Integer> flusher = builder.build();
//        new PerformanceChecker().execute(new Runnable() {
//            @Override
//            public void run() {
//                for (int i = 0; i < 10240000 * 3; i++) {
//                    flusher.add(i);
//                }
//            }
//        }, "a", 4, 2000);
//        flusher.shutdown();
    }

    @Test
    public void test2() throws Exception {
        {
            final AtomicLong result = new AtomicLong();
            final ArrayBlockingQueue<Object> queue1 = new ArrayBlockingQueue<Object>(1024 * 16 * 16);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            if (queue1.poll(1, TimeUnit.SECONDS) != null) {
                                result.incrementAndGet();
                            }
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }).start();

            new PerformanceChecker().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10240000 * 3; i++) {
                        try {
                            queue1.put(i);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }, "a", 4, 2000);
            Thread.sleep(3 * 1000);
            System.out.println(result.get());
        }
        {
            final AtomicLong result = new AtomicLong();
            final ArrayUnblockingQueue<Object> queue2 = new ArrayUnblockingQueue<>("thread", 1024 * 16 * 16, new ArrayUnblockingQueue.Processor<Object>() {
                @Override
                public void process(Object data) {
                    result.incrementAndGet();
                }

                @Override
                public void onTimeout(long current) {
                    System.out.println("timeout: " + current);
                }

                @Override
                public void onThrowable(Object data, Throwable e) {
                    e.printStackTrace();
                }
            }, new ArrayUnblockingQueue.SleepingWaitor(200));

            queue2.start();
            new PerformanceChecker().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10240000 * 3; i++) {
                        queue2.put(i);
                    }
                }
            }, "a", 4, 2000);
            Thread.sleep(6 * 1000);
            queue2.stop();
            System.out.println(result.get());
        }
        {
            final AtomicLong result = new AtomicLong();
            InvokeFlusher.Builder<Integer> builder = new InvokeFlusher.Builder<Integer>().setBufferSize(1024 * 16 * 16).setNotifySize(128).setNamePrefix("test").setWaitStrategy(new SleepingWaitStrategy(200));

            InvokeFlusher.EventListener<Integer>[] group1 = new InvokeFlusher.EventListener[1];
            group1[0] = new InvokeFlusher.EventListener<Integer>() {
                @Override
                public void onException(Throwable e, long sequence, Integer event) {

                }

                @Override
                public void onEvent(Integer event, boolean endOfBatch) throws Exception {
                    result.incrementAndGet();
                }
            };
            builder.addListenerGroup(group1);

            final InvokeFlusher<Integer> flusher = builder.build();
            new PerformanceChecker().execute(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10240000 * 3; i++) {
                        flusher.add(i);
                    }
                }
            }, "a", 4, 2000);
            flusher.shutdown();
            System.out.println(result.get());
        }

    }
}
