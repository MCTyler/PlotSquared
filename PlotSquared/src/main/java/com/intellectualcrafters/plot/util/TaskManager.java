package com.intellectualcrafters.plot.util;

import com.intellectualcrafters.plot.PlotMain;

public class TaskManager {
    public static void runTask(final Runnable r) {
        PlotMain.getMain().getServer().getScheduler().runTaskAsynchronously(PlotMain.getMain(), r);
    }
    
    public static void runTaskLater(final Runnable r, int delay) {
        PlotMain.getMain().getServer().getScheduler().runTaskLater(PlotMain.getMain(), r, delay);
    }
}
