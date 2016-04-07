package org.danielli.logging;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import org.danielli.common.clock.CachedClock;
import org.danielli.common.clock.Clock;
import org.danielli.logging.exception.ExceptionHandler;
import org.danielli.logging.handler.FileHandler;
import org.danielli.logging.handler.RollingFileHandler;
import org.danielli.logging.handler.support.DefaultFileHandler;
import org.danielli.logging.handler.support.DefaultRollingFileHandler;
import org.danielli.logging.roll.DefaultRollover;
import org.danielli.logging.roll.Rollover;
import org.danielli.logging.roll.pattern.FilePattern;
import org.danielli.logging.roll.trigger.CompositeTrigger;
import org.danielli.logging.roll.trigger.SizeBasedTrigger;
import org.danielli.logging.roll.trigger.TimeBasedTrigger;
import org.danielli.logging.roll.trigger.Trigger;
import org.danielli.logging.support.AsyncLogger;
import org.danielli.logging.support.DefaultLogger;
import org.danielli.logging.support.FilterableLogger;
import org.danielli.logging.support.RollingLogger;

import java.util.List;

/**
 * {@link Logger} 构造器。
 *
 * @author Daniel Li
 * @since 8 August 2015
 */
public abstract class LoggerBuilder {

    public static DefaultLoggerBuilder of(String fileName, ExceptionHandler exceptionHandler) {
        return new DefaultLoggerBuilder(fileName, exceptionHandler);
    }

    private static Logger filter(Logger logger, Logger.Filter... filters) {
        Preconditions.checkNotNull(filters);

        return new FilterableLogger(logger, filters);
    }

    public abstract Logger build();

    public static class DefaultLoggerBuilder extends LoggerBuilder {

        protected boolean immediateFlush = false;
        protected String fileName;

        protected boolean isAppend = true;
        protected int fileBufferSize = 512 * 1024;
        protected boolean useDirectMemory = true;
        protected Clock clock = new CachedClock();

        protected ExceptionHandler exceptionHandler;

        private DefaultLoggerBuilder(String fileName, ExceptionHandler exceptionHandler) {
            this.fileName = Preconditions.checkNotNull(fileName);
            this.exceptionHandler = Preconditions.checkNotNull(exceptionHandler);
        }

        public DefaultLoggerBuilder setImmediateFlush(boolean immediateFlush) {
            this.immediateFlush = immediateFlush;
            return this;
        }

        public DefaultLoggerBuilder setFileName(String fileName) {
            this.fileName = Preconditions.checkNotNull(fileName);
            return this;
        }

        public DefaultLoggerBuilder setAppend(boolean isAppend) {
            this.isAppend = isAppend;
            return this;
        }

        public DefaultLoggerBuilder setFileBufferSize(int bufferSize) {
            Preconditions.checkArgument(bufferSize > 0);
            this.fileBufferSize = bufferSize;
            return this;
        }

        public DefaultLoggerBuilder setUseDirectMemory(boolean useDirectMemory) {
            this.useDirectMemory = useDirectMemory;
            return this;
        }

        public DefaultLoggerBuilder setClock(Clock clock) {
            this.clock = Preconditions.checkNotNull(clock);
            return this;
        }

        public DefaultLoggerBuilder setExceptionHandler(ExceptionHandler exceptionHandler) {
            this.exceptionHandler = Preconditions.checkNotNull(exceptionHandler);
            return this;
        }

        public AsyncLoggerBuilder async() {
            return new AsyncLoggerBuilder(build(), exceptionHandler);
        }

        public RollingLoggerBuilder rolling(String filePattern) {
            return new RollingLoggerBuilder(this, filePattern);
        }

        @Override
        public Logger build() {
            FileHandler fileHandler = new DefaultFileHandler(fileName, isAppend, fileBufferSize, useDirectMemory, clock,
                    exceptionHandler);
            return new DefaultLogger<>(immediateFlush, fileHandler);
        }
    }

    public static class RollingLoggerBuilder extends LoggerBuilder {

        private final DefaultLoggerBuilder builder;

        protected String filePattern;
        protected long maxFileSize = 50 * 1024 * 1024;
        protected int interval = 1;
        protected boolean modulate = true;

        protected int minIndex = 1;
        protected int maxIndex = 30;
        protected boolean useMax = false;
        protected int compressionBufferSize = 1024 * 512;

        private RollingLoggerBuilder(DefaultLoggerBuilder builder, String filePattern) {
            this.builder = Preconditions.checkNotNull(builder);
            this.filePattern = Preconditions.checkNotNull(filePattern);
        }

        public RollingLoggerBuilder setInterval(int interval) {
            Preconditions.checkArgument(interval > 0);
            this.interval = interval;
            return this;
        }

        public RollingLoggerBuilder setModulate(boolean modulate) {
            this.modulate = modulate;
            return this;
        }

        public RollingLoggerBuilder setFilePattern(String filePattern) {
            this.filePattern = Preconditions.checkNotNull(filePattern);
            return this;
        }

        public RollingLoggerBuilder setMaxFileSize(long maxFileSize) {
            Preconditions.checkArgument(maxFileSize >= 0);
            this.maxFileSize = maxFileSize;
            return this;
        }

        public RollingLoggerBuilder setBackupSize(int backupSize) {
            Preconditions.checkArgument(backupSize >= 0);
            this.maxIndex = backupSize;
            return this;
        }

        public RollingLoggerBuilder setUseMax(boolean useMax) {
            this.useMax = useMax;
            return this;
        }

        public AsyncLoggerBuilder async() {
            return new AsyncLoggerBuilder(build(), builder.exceptionHandler);
        }

        @Override
        public Logger build() {
            ExceptionHandler exceptionHandler = builder.exceptionHandler;
            String fileName = builder.fileName;
            boolean isAppend = builder.isAppend;
            int fileBufferSize = builder.fileBufferSize;
            boolean useDirectMemory = builder.useDirectMemory;
            Clock clock = builder.clock;
            boolean immediateFlush = builder.immediateFlush;

            FilePattern filePattern = new FilePattern(this.filePattern, clock);

            List<Trigger<RollingFileHandler>> triggers = Lists.newArrayListWithCapacity(2);
            if (maxFileSize > 0) {
                triggers.add(new SizeBasedTrigger<>(maxFileSize));
            }
            if (filePattern.containDate()) {
                triggers.add(new TimeBasedTrigger<>(interval, modulate));
            }

            @SuppressWarnings("unchecked") Trigger<RollingFileHandler> trigger = new CompositeTrigger<>(triggers.toArray(new Trigger[triggers.size()]));

            Rollover rollover = new DefaultRollover(minIndex, maxIndex, useMax, 0, compressionBufferSize, exceptionHandler);
            RollingFileHandler fileHandler = new DefaultRollingFileHandler(fileName, filePattern, isAppend, fileBufferSize,
                    useDirectMemory, clock, trigger, rollover, exceptionHandler);
            return new RollingLogger<>(immediateFlush, fileHandler);
        }

    }

    public static class AsyncLoggerBuilder extends LoggerBuilder {

        protected final Logger logger;
        protected final ExceptionHandler exceptionHandler;
        protected WaitStrategy waitStrategy = new YieldingWaitStrategy();
        protected ProducerType producerType = ProducerType.MULTI;
        protected int bufferSize = 512 * 1024;
        protected int notifySize = 1024;

        private AsyncLoggerBuilder(Logger logger, ExceptionHandler exceptionHandler) {
            this.logger = Preconditions.checkNotNull(logger);
            this.exceptionHandler = Preconditions.checkNotNull(exceptionHandler);
        }

        public AsyncLoggerBuilder setBufferSize(int bufferSize) {
            Preconditions.checkArgument(bufferSize > 0);
            this.bufferSize = bufferSize;
            return this;
        }

        public AsyncLoggerBuilder setNotifySize(int notifySize) {
            Preconditions.checkArgument(notifySize > 0);
            this.notifySize = notifySize;
            return this;
        }

        public AsyncLoggerBuilder setProducerType(ProducerType producerType) {
            this.producerType = Preconditions.checkNotNull(producerType);
            return this;
        }

        public AsyncLoggerBuilder setWaitStrategy(WaitStrategy waitStrategy) {
            this.waitStrategy = Preconditions.checkNotNull(waitStrategy);
            return this;
        }

        @Override
        public Logger build() {
            return new AsyncLogger(logger, waitStrategy, producerType, bufferSize, notifySize, exceptionHandler);
        }
    }

}
