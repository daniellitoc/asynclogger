package org.danielli.common.clock;

/**
 * 系统时钟。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class SystemClock implements Clock {

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
