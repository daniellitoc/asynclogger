package org.danielli.logging;

import org.danielli.common.clock.CachedClock;
import org.danielli.common.clock.Clock;
import org.danielli.logging.exception.ExceptionHandler;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@link org.danielli.logging.Logger} 测试。
 *
 * @author Daniel Li
 * @since 13 September 2015
 */
public class AsyncLoggerTest {

    private ExecutorService executorService = Executors.newFixedThreadPool(4);
    private CountDownLatch countDownLatch = new CountDownLatch(4);

    @Test
    public void testLog() throws InterruptedException {
        final Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler())
                .rolling("test.%index.log.gz").async().build();
        for (int i = 0, length = (int) countDownLatch.getCount(); i < length; i++) {
            executorService.execute(new Runnable() {

                @Override
                public void run() {
                    for (int i = 0; i < 10000000; i++) {
                        logger.write(new TestLoggerEvent("aaaaaaaaaaaaaaaaaaa" + i));
                    }
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        executorService.shutdown();
        logger.close();
    }

    private static class TestLoggerEvent implements LoggerEvent {

        private static Clock clock = new CachedClock();

        private long timeMillis;
        private String message;

        public TestLoggerEvent(String message) {
            this.message = message;
            this.timeMillis = clock.currentTimeMillis();
        }

        @Override
        public long getTimeMillis() {
            return this.timeMillis;
        }

        @Override
        public byte[] toByteArray() {
            return (String.valueOf(timeMillis) + "|" + message + "\n").getBytes();
        }
    }

    private static class TestExceptionHandler implements ExceptionHandler {

        @Override
        public void handleEventException(String msg, Throwable e, LoggerEvent event) {
            System.out.println(3);
            e.printStackTrace();
        }

        @Override
        public void handleException(String msg, Throwable e) {
            System.out.println(2);
            e.printStackTrace();
        }

        @Override
        public void handleEvent(String msg, LoggerEvent event) {
            System.out.println(1);
        }

        @Override
        public void handle(String msg) {
            System.out.println(0);
        }
    }
}
