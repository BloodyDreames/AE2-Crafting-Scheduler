package dev.BloodyDreamsWork.ae2_scheduler.testplots;

import appeng.server.testplots.TestPlotClass;

/**
 * AE2 19.x discovers plot classes through this annotation. AE2's scanner walks
 * {@code Class#getMethods()}, which includes the inherited public static {@code @TestPlot} methods,
 * so the plots themselves stay in the shared {@link SchedulerTestPlotsBase}.
 */
@TestPlotClass
public final class SchedulerTestPlots extends SchedulerTestPlotsBase {
    private SchedulerTestPlots() {
    }
}
