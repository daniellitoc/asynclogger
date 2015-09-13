# AsyncLogger #

异步日志，参考Log4j2的异步日志和文件轮转机制，重新抽象结构实现。

## 用途 ##

* 对吞吐量有所要求（RingBuffer + ByteBuffer + FileChannel）。
* 不希望耦合或依赖log4j2/logback等日志框架。需要单独隔离。

## 特点 ##

* CachedClock：后台线程计算时间。
* ParallelFlusher：异步刷新器，生产者/消费者模式（无锁队列），同一事件只被一个消费者消费。多消费者并行消费。单条”通知“模式。
* InvokeFlusher：异步刷新器，生产者/消费者模式（无锁队列）。同一事件被所有消费者消费，多消费者之间可控制消费顺序（通过Group）。批量”通知“模式。
* BatchForwarder：批量聚合器，用于聚合单条记录转为批量。
* DefaultLogger：普通日志（ByteBuffer + FileChannel）。
* RollingLogger：轮转日志（支持日期格式自动识别、索引、压缩；按时间、文件大小识别轮转）
* AsyncLogger：采用InvokeFlusher实现。
* FilterableLogger：支持过滤的日志。
* LoggerBuilder：用于生成日志（DefaultLogger/RollingLogger/AsyncLogger/FilterableLogger）的构造器。

## 相关属性 ##

## DefaultLogger ##

* immediateFlush：每次都同步落盘，默认为false。
* fileName：文件名，必填。
* isAppend：是否对日志进行追加，默认为true。
* fileBufferSize：ByteBuffer申请大小，默认512 * 1024。
* useDirectMemory：是否使用DirectMemory，默认为true。
* clock：时钟，默认采用CachedClock。
* exceptionHandler：异常处理器。

## DefaultLogger ##

* filePattern：文件pattern，必填，支持日期和索引，如test.%d{yyyy-MM-dd}.%index.log.gz。
* maxFileSize：轮转条件之最大文件大小，默认为50 * 1024 * 1024，设置为0表示不限制大小。
* interval：轮转条件之时间间隔，默认为1，视pattern是否包含日期而起作用，如格式为yyyy-MM-dd，则间隔为day级别。
* modulate：轮转条件之时间是否截断，默认为true，视pattern是否包含日期而起作用，如格式为yyyy-MM-dd-HH，interval为1，当前时间为02:12，若modulate为false，则03:12进行轮转，若为true，则表示3:00进行轮转。
* minIndex：视是否包含%index而起作用，默认为1.
* maxIndex：视是否包含%index而起作用，默认为30。
* useMax：视是否包含%index而起作用，默认为false，表示使用minIndex、
* compressionBufferSize：压缩buffer大小，默认为1024 * 512。

## AsyncLogger ##

* waitStrategy：等待策略，参考InvokeFlusher，可根据延迟、性能等情况，自行选择（自旋、让步、让步 + 等待、锁和条件）。
* producerType：标识生产者类型，单生产者和多生产者，默认为多生产者。
* bufferSize：RingBuffer大小，默认为512 * 1024。
* notifySize：通知大小。默认为1024。用于手动报告RingBuffer当前位置。

## 使用 ##

    // 一直输出到test.log。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).build();

    // 一直输出到test.log，使用HeapByteBuffer。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).setUseDirectMemory(false).build();

    // 一直输出到test.log，使用异步方式。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).async().build();

    // 一直输出到test.log，使用异步方式，设置RingBuffer大小。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).async().setBufferSize(1024 * 20).build();

    // 一直输出到test.log，带有日志轮转功能。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).rolling("test.%d{yyy-MM-dd}.%index.log.gz").build();

    // 一直输出到test.log，带有日志轮转功能。yyy-MM-dd级别下最多保留10份。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).rolling("test.%d{yyy-MM-dd}.%index.log.gz").setBackupSize(10).build();

    // 一直输出到test.log，使用异步方式，带有日志轮转功能。yyy-MM-dd级别下最多保留10份。
    Logger logger = LoggerBuilder.of("test.log", new TestExceptionHandler()).rolling("test.%d{yyy-MM-dd}.%index.log.gz").setBackupSize(10).async().build();

    // 带有过滤功能的日志。
    Logger filterLogger = LoggerBuilder.filter(logger, new Filter1(), new Filter2());