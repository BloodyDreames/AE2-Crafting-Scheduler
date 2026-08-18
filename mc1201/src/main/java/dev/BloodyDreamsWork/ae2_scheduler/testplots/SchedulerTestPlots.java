package dev.BloodyDreamsWork.ae2_scheduler.testplots;

/**
 * AE2 15.x has no {@code @TestPlotClass} annotation; plot classes are handed to
 * {@code TestPlots.addPlotClass} instead, which the 1.20.1 loader entry points do. AE2's scanner
 * walks {@code Class#getMethods()}, which includes the inherited public static {@code @TestPlot}
 * methods, so the plots themselves stay in the shared {@link SchedulerTestPlotsBase}.
 */
public final class SchedulerTestPlots extends SchedulerTestPlotsBase {
    private SchedulerTestPlots() {
    }
}
