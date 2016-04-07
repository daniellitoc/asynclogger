package org.danielli.logging.roll.pattern.format;

import com.google.common.collect.Maps;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * 日志格式化器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public class DateFormatter implements Formatter {

    private final String pattern;
    private final Frequency frequency;

    public DateFormatter(String pattern) {
        this.pattern = pattern;
        this.frequency = Frequency.of(pattern);
    }

    private static String format(String pattern, Date date) {
        return new SimpleDateFormat(pattern).format(date);
    }

    public Frequency getFrequency() {
        return frequency;
    }

    @Override
    public void format(StringBuilder source, Object... arguments) {
        for (Object argument : arguments) {
            if (argument instanceof Date) {
                source.append(DateFormatter.format(this.pattern, (Date) argument));
                break;
            }
        }
    }

    public enum Frequency {

        MINUTE("m") {
            @Override
            protected Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus) {
                calendar.set(Calendar.MONTH, current.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_YEAR, current.get(Calendar.DAY_OF_YEAR));
                calendar.set(Calendar.HOUR_OF_DAY, current.get(Calendar.HOUR_OF_DAY));
                calendar.set(Calendar.MINUTE, current.get(Calendar.MINUTE));

                increment(calendar, Calendar.MINUTE, increment, modulus);
                long nextTime = calendar.getTimeInMillis();
                calendar.add(Calendar.MINUTE, -1);
                long nextFileTime = calendar.getTimeInMillis();
                return Maps.immutableEntry(nextTime, nextFileTime);
            }
        },

        HOURLY("H") {
            @Override
            protected Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus) {
                calendar.set(Calendar.MONTH, current.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_YEAR, current.get(Calendar.DAY_OF_YEAR));
                calendar.set(Calendar.HOUR_OF_DAY, current.get(Calendar.HOUR_OF_DAY));

                increment(calendar, Calendar.HOUR_OF_DAY, increment, modulus);
                long nextTime = calendar.getTimeInMillis();
                calendar.add(Calendar.HOUR_OF_DAY, -1);
                long nextFileTime = calendar.getTimeInMillis();
                return Maps.immutableEntry(nextTime, nextFileTime);
            }
        },

        DAILY("d") {
            @Override
            protected Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus) {
                calendar.set(Calendar.MONTH, current.get(Calendar.MONTH));
                calendar.set(Calendar.DAY_OF_YEAR, current.get(Calendar.DAY_OF_YEAR));

                increment(calendar, Calendar.DAY_OF_YEAR, increment, modulus);
                long nextTime = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_YEAR, -1);
                long nextFileTime = calendar.getTimeInMillis();
                return Maps.immutableEntry(nextTime, nextFileTime);
            }
        },

        WEEKLY("w") {
            @Override
            protected Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus) {
                calendar.set(Calendar.MONTH, current.get(Calendar.MONTH));

                calendar.set(Calendar.WEEK_OF_YEAR, current.get(Calendar.WEEK_OF_YEAR));
                increment(calendar, Calendar.WEEK_OF_YEAR, increment, modulus);
                calendar.set(Calendar.DAY_OF_WEEK, current.getFirstDayOfWeek());
                long nextTime = calendar.getTimeInMillis();
                calendar.add(Calendar.WEEK_OF_YEAR, -1);
                long nextFileTime = calendar.getTimeInMillis();
                return Maps.immutableEntry(nextTime, nextFileTime);
            }
        },

        MONTHLY("M") {
            @Override
            protected Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus) {
                calendar.set(Calendar.MONTH, current.get(Calendar.MONTH));

                increment(calendar, Calendar.MONTH, increment, modulus);
                long nextTime = calendar.getTimeInMillis();
                calendar.add(Calendar.MONTH, -1);
                long nextFileTime = calendar.getTimeInMillis();
                return Maps.immutableEntry(nextTime, nextFileTime);
            }
        },

        ANNUALLY("y") {
            @Override
            protected Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus) {
                Frequency.increment(calendar, Calendar.YEAR, increment, modulus);
                long nextTime = calendar.getTimeInMillis();
                calendar.add(Calendar.YEAR, -1);
                long nextFileTime = calendar.getTimeInMillis();
                return Maps.immutableEntry(nextTime, nextFileTime);
            }
        };

        private String match;

        Frequency(String match) {
            this.match = match;
        }

        private static void increment(Calendar cal, int type, int increment, boolean modulate) {
            int interval = modulate ? increment - (cal.get(type) % increment) : increment;
            cal.add(type, interval);
        }

        public static Frequency of(String pattern) throws IllegalArgumentException {
            for (Frequency frequency : Frequency.values()) {
                if (frequency.match(pattern)) {
                    return frequency;
                }
            }
            throw new IllegalArgumentException("pattern not support");
        }

        public Map.Entry<Long, Long> getNextTime(long currentMillis, int increment, boolean modulus) {
            Calendar current = Calendar.getInstance();
            current.setTimeInMillis(currentMillis);
            Calendar calendar = Calendar.getInstance();

            calendar.set(current.get(Calendar.YEAR), Calendar.JANUARY, 1, 0, 0, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return doGetNextTime(calendar, current, increment, modulus);
        }

        protected abstract Map.Entry<Long, Long> doGetNextTime(Calendar calendar, Calendar current, int increment, boolean modulus);

        private boolean match(String pattern) {
            return pattern.contains(match);
        }
    }
}
