package org.danielli.common.clock;

import java.util.concurrent.locks.LockSupport;

/**
 * 具备缓存功能的时钟。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class CachedClock implements Clock {

    private static final int countThreshold = 1000;
    static {
        setCurrent();
        final Thread updater = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    setCurrent();
                    LockSupport.parkNanos(1000 * 1000);
                }
            }
        }, "CachedClock Updater Thread");
        updater.setDaemon(true);
        updater.start();
    }
    private static volatile long current;
    private static short count = 0;

    public CachedClock() {

    }

    private static void setCurrent() {
        current = System.currentTimeMillis();
    }

    @Override
    public long currentTimeMillis() {
        if (count++ > countThreshold) {
            setCurrent();
            count = 0;
        }
        return current;
    }
}
