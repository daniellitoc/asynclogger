package org.danielli.common.concurrent.async;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 修改自druid。
 *
 * @author Daniel Li
 * @since 14 四月 2016
 */
public class PerformanceChecker {

    public static double getLoad() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    public static long getYoungGC() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName;
            if (mbeanServer.isRegistered(new ObjectName("java.lang:type=GarbageCollector,name=ParNew"))) {
                objectName = new ObjectName("java.lang:type=GarbageCollector,name=ParNew");
            } else if (mbeanServer.isRegistered(new ObjectName("java.lang:type=GarbageCollector,name=Copy"))) {
                objectName = new ObjectName("java.lang:type=GarbageCollector,name=Copy");
            } else {
                objectName = new ObjectName("java.lang:type=GarbageCollector,name=PS Scavenge");
            }

            return (Long) mbeanServer.getAttribute(objectName, "CollectionCount");
        } catch (Exception e) {
            throw new RuntimeException("error");
        }
    }

    public static long getFullGC() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName objectName;

            if (mbeanServer.isRegistered(new ObjectName("java.lang:type=GarbageCollector,name=ConcurrentMarkSweep"))) {
                objectName = new ObjectName("java.lang:type=GarbageCollector,name=ConcurrentMarkSweep");
            } else if (mbeanServer.isRegistered(new ObjectName("java.lang:type=GarbageCollector,name=MarkSweepCompact"))) {
                objectName = new ObjectName("java.lang:type=GarbageCollector,name=MarkSweepCompact");
            } else {
                objectName = new ObjectName("java.lang:type=GarbageCollector,name=PS MarkSweep");
            }

            return (Long) mbeanServer.getAttribute(objectName, "CollectionCount");
        } catch (Exception e) {
            throw new RuntimeException("error");
        }
    }

    public void execute(final Runnable runnable, String threadName, int threadCount, long loadPeriod) throws Exception {

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch endLatch = new CountDownLatch(threadCount);
        final CountDownLatch dumpLatch = new CountDownLatch(1);
        final List<Double> loads = new ArrayList<>();

        ScheduledExecutorService executorService = null;
        if (loadPeriod >= 0) {
            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    loads.add(getLoad());
                }
            }, 0, loadPeriod, TimeUnit.MILLISECONDS);
        }

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; ++i) {
            Thread thread = new Thread() {

                public void run() {
                    try {
                        startLatch.await();
                        runnable.run();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    endLatch.countDown();

                    try {
                        dumpLatch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
            threads[i] = thread;
            thread.start();
        }
        long startMillis = System.currentTimeMillis();
        long startYGC = getYoungGC();
        long startFullGC = getFullGC();
        startLatch.countDown();
        endLatch.await();

        long[] threadIdArray = new long[threads.length];
        for (int i = 0; i < threads.length; ++i) {
            threadIdArray[i] = threads[i].getId();
        }
        ThreadInfo[] threadInfoArray = ManagementFactory.getThreadMXBean().getThreadInfo(threadIdArray);

        dumpLatch.countDown();
        if (executorService != null) {
            executorService.shutdown();
        }

        long blockedCount = 0;
        long waitedCount = 0;
        for (int i = 0; i < threadInfoArray.length; ++i) {
            ThreadInfo threadInfo = threadInfoArray[i];
            blockedCount += threadInfo.getBlockedCount();
            waitedCount += threadInfo.getWaitedCount();
        }

        long millis = System.currentTimeMillis() - startMillis;
        long ygc = getYoungGC() - startYGC;
        long fullGC = getFullGC() - startFullGC;

        BigDecimal avgLoad = BigDecimal.valueOf(0);
        for (Double load: loads) {
            avgLoad = avgLoad.add(BigDecimal.valueOf(load));
        }
        System.out.println("thread " + threadCount + " " + threadName + "\nmillis : " + NumberFormat.getInstance().format(millis)
                + "\nYGC " + ygc + " FGC " + fullGC +
                "\nblocked " + NumberFormat.getInstance().format(blockedCount) + " waited " + NumberFormat.getInstance().format(waitedCount) +
                "\navgLoad " + avgLoad.divide(BigDecimal.valueOf(loads.size()), 2) + "\nloads " + loads);
    }
}
