////////////////////////////////////////////////////////////////////////////////////////////////////
// PlotSquared - A plot manager and world generator for the Bukkit API                             /
// Copyright (c) 2014 IntellectualSites/IntellectualCrafters                                       /
//                                                                                                 /
// This program is free software; you can redistribute it and/or modify                            /
// it under the terms of the GNU General Public License as published by                            /
// the Free Software Foundation; either version 3 of the License, or                               /
// (at your option) any later version.                                                             /
//                                                                                                 /
// This program is distributed in the hope that it will be useful,                                 /
// but WITHOUT ANY WARRANTY; without even the implied warranty of                                  /
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                                   /
// GNU General Public License for more details.                                                    /
//                                                                                                 /
// You should have received a copy of the GNU General Public License                               /
// along with this program; if not, write to the Free Software Foundation,                         /
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA                               /
//                                                                                                 /
// You can contact us via: support@intellectualsites.com                                           /
////////////////////////////////////////////////////////////////////////////////////////////////////

package com.intellectualcrafters.plot.flag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.intellectualcrafters.plot.PlotMain;
import com.intellectualcrafters.plot.config.C;
import com.intellectualcrafters.plot.database.DBFunc;
import com.intellectualcrafters.plot.events.PlotFlagAddEvent;
import com.intellectualcrafters.plot.events.PlotFlagRemoveEvent;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotCluster;
import com.intellectualcrafters.plot.object.PlotSettings;
import com.intellectualcrafters.plot.object.PlotWorld;

/**
 * Flag Manager Utility
 *
 * @author Citymonstret
 * @author Empire92
 */
@SuppressWarnings("unused") public class FlagManager {

    // TODO add some flags
    // - Plot clear interval
    // - Mob cap
    // - customized plot composition

    private final static ArrayList<AbstractFlag> flags = new ArrayList<>();

    /**
     * Register an AbstractFlag with PlotSquared
     *
     * @param flag Flag to register
     *
     * @return success?
     */
    public static boolean addFlag(final AbstractFlag af) {
        PlotMain.sendConsoleSenderMessage(C.PREFIX.s() + "&8 - Adding flag: &7" + af);
        String key = af.getKey();
        if (PlotMain.getAllPlotsRaw() != null) {
            for (Plot plot : PlotMain.getPlots()) {
                for (Flag flag : plot.settings.flags) {
                    if (flag.getAbstractFlag().getKey().equals(af)) {
                        flag.setKey(af);
                    }
                }
            }
        }
        return (getFlag(af.getKey()) == null) && flags.add(af);
    }

    public static Flag getSettingFlag(String world, PlotSettings settings, String flag) {
    	ArrayList<Flag> flags = new ArrayList<>();
        if (settings.flags != null && settings.flags.size() > 0) {
        	flags.addAll(settings.flags);
        }
        PlotWorld plotworld = PlotMain.getWorldSettings(world);
        if (plotworld != null && plotworld.DEFAULT_FLAGS != null && plotworld.DEFAULT_FLAGS.length > 0) {
            flags.addAll(Arrays.asList(plotworld.DEFAULT_FLAGS));
        }
        for (final Flag myflag : flags) {
            if (myflag.getKey().equals(flag)) {
                return myflag;
            }
        }
        return null;
    }
    
    /**
     * Get the value of a flag for a plot (respects flag defaults)
     * @param plot
     * @param flag
     * @return
     */
    public static Flag getPlotFlag(Plot plot, String flag) {
        return getSettingFlag(plot.world, plot.settings, flag);
    }
    
    public static boolean isPlotFlagTrue(Plot plot, String strFlag) {
    	Flag flag = getPlotFlag(plot, strFlag);
    	if (flag == null) {
    		return false;
    	}
    	if (flag.getValue() instanceof Boolean) {
    		return (boolean) flag.getValue();
    	}
    	return false;
    }
    
    /**
     * Get the value of a flag for a plot (ignores flag defaults)
     * @param plot
     * @param flag
     * @return
     */
    public static Flag getPlotFlagAbs(Plot plot, String flag) {
    	return getSettingFlagAbs(plot.settings, flag);
    }
    
    public static Flag getSettingFlagAbs(PlotSettings settings, String flag) {
    	if (settings.flags == null || settings.flags.size() == 0) {
        	return null;
        }
        for (final Flag myflag : settings.flags) {
            if (myflag.getKey().equals(flag)) {
                return myflag;
            }
        }
        return null;
    }
    
    /**
     * Add a flag to a plot
     * @param plot
     * @param flag
     */
    public static boolean addPlotFlag(Plot plot, final Flag flag) {
        final PlotFlagAddEvent event = new PlotFlagAddEvent(flag, plot);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        final Flag hasFlag = getPlotFlag(plot, flag.getKey());
        if (hasFlag != null) {
            plot.settings.flags.remove(hasFlag);
        }
        plot.settings.flags.add(flag);
        DBFunc.setFlags(plot.world, plot, plot.settings.flags);
        return true;
    }
    
    public static boolean addClusterFlag(PlotCluster cluster, final Flag flag) {
    	//TODO plot cluster flag event
        final Flag hasFlag = getSettingFlag(cluster.world, cluster.settings, flag.getKey());
        if (hasFlag != null) {
            cluster.settings.flags.remove(hasFlag);
        }
        cluster.settings.flags.add(flag);
        DBFunc.setFlags(cluster, cluster.settings.flags);
        return true;
    }
    
    /**
     * 
     * @param plot
     * @return
     */
    public static Set<Flag> getPlotFlags(Plot plot) {
        return getSettingFlags(plot.world, plot.settings);
    }
    
    public static Set<Flag> getSettingFlags(String world, PlotSettings settings) {
        Set<Flag> plotflags = settings.flags;
        PlotWorld plotworld = PlotMain.getWorldSettings(world);
        if (plotworld != null && plotworld.DEFAULT_FLAGS != null && plotworld.DEFAULT_FLAGS.length > 0) {
            HashSet<String> flagStrings = new HashSet<>();
            for (Flag flag : plotflags) {
                flagStrings.add(flag.getKey());
            }
            for (Flag newflag : plotworld.DEFAULT_FLAGS) {
                if (!flagStrings.contains(newflag.getKey())) {
                    plotflags.add(newflag);
                }
            }
        }
        return plotflags;
    }
    
    public static boolean removePlotFlag(Plot plot, String flag) {
        final Flag hasFlag = getPlotFlag(plot, flag);
        if (hasFlag != null) {
            Flag flagObj = FlagManager.getPlotFlagAbs(plot, flag);
            if (flagObj != null) {
                final PlotFlagRemoveEvent event = new PlotFlagRemoveEvent(flagObj, plot);
                Bukkit.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return false;
                }
                plot.settings.flags.remove(hasFlag);
                DBFunc.setFlags(plot.world, plot, plot.settings.flags);
                return true;
            }
        }
        return false;
    }
    
    public static boolean removeClusterFlag(PlotCluster cluster, String flag) {
        final Flag hasFlag = getSettingFlag(cluster.world, cluster.settings, flag);
        if (hasFlag != null) {
            Flag flagObj = FlagManager.getSettingFlagAbs(cluster.settings, flag);
            if (flagObj != null) {
            	//TODO cluster flag add event
                cluster.settings.flags.remove(hasFlag);
                DBFunc.setFlags(cluster, cluster.settings.flags);
                return true;
            }
        }
        return false;
    }

    public static void setPlotFlags(Plot plot, Set<Flag> flags) {
        if (flags == null) {
            plot.settings.flags = new HashSet<>();
            DBFunc.setFlags(plot.world, plot, plot.settings.flags);
            return;
        }
        plot.settings.flags = flags;
        DBFunc.setFlags(plot.world, plot, plot.settings.flags);
    }
    
    public static void setClusterFlags(PlotCluster cluster, Set<Flag> flags) {
        if (flags == null) {
            cluster.settings.flags = new HashSet<>();
            DBFunc.setFlags(cluster, cluster.settings.flags);
            return;
        }
        cluster.settings.flags = flags;
        DBFunc.setFlags(cluster, cluster.settings.flags);
    }

    public static Flag[] removeFlag(final Flag[] flags, final String r) {
        final Flag[] f = new Flag[flags.length - 1];
        int index = 0;
        for (final Flag flag : flags) {
            if (!flag.getKey().equals(r)) {
                f[index++] = flag;
            }
        }
        return f;
    }

    public static Set<Flag> removeFlag(final Set<Flag> flags, final String r) {
        final HashSet<Flag> newflags = new HashSet<>();
        int index = 0;
        for (final Flag flag : flags) {
            if (!flag.getKey().equalsIgnoreCase(r)) {
                newflags.add(flag);
            }
        }
        return newflags;
    }
    
    /**
     * Get a list of registered AbstractFlag objects
     *
     * @return List (AbstractFlag)
     */
    public static List<AbstractFlag> getFlags() {
        return flags;
    }

    /**
     * Get a list of registerd AbstragFlag objects based on player permissions
     *
     * @param player with permissions
     *
     * @return List (AbstractFlag)
     */
    public static List<AbstractFlag> getFlags(final Player player) {
        final List<AbstractFlag> returnFlags = new ArrayList<>();
        for (final AbstractFlag flag : flags) {
            if (player.hasPermission("plots.set.flag." + flag.getKey().toLowerCase())) {
                returnFlags.add(flag);
            }
        }
        return returnFlags;
    }

    /**
     * Get an AbstractFlag by a string Returns null if flag does not exist
     *
     * @param string Flag Key
     *
     * @return AbstractFlag
     */
    public static AbstractFlag getFlag(final String string) {
        for (final AbstractFlag flag : flags) {
            if (flag.getKey().equalsIgnoreCase(string)) {
                return flag;
            }
        }
        return null;
    }

    /**
     * Get an AbstractFlag by a string
     *
     * @param string Flag Key
     * @param create If to create the flag if it does not exist
     *
     * @return AbstractFlag
     */
    public static AbstractFlag getFlag(final String string, final boolean create) {
        if ((getFlag(string) == null) && create) {
            final AbstractFlag flag = new AbstractFlag(string);
            addFlag(flag);
            return flag;
        }
        return getFlag(string);
    }

    /**
     * Remove a registered AbstractFlag
     *
     * @param flag Flag Key
     *
     * @return boolean Result of operation
     */
    public static boolean removeFlag(final AbstractFlag flag) {
        return flags.remove(flag);
    }

    public static Flag[] parseFlags(final List<String> flagstrings) {
        final Flag[] flags = new Flag[flagstrings.size()];
        for (int i = 0; i < flagstrings.size(); i++) {
            final String[] split = flagstrings.get(i).split(";");
            if (split.length == 1) {
                flags[i] = new Flag(getFlag(split[0], true), "");
            } else {
                flags[i] = new Flag(getFlag(split[0], true), split[1]);
            }
        }
        return flags;
    }
}
